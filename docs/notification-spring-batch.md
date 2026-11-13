# 전체 공지 Spring Batch 전환

## PAAR (Problem · Analysis · Action · Result)

### Problem
활성 회원 전체에 공지 알림을 저장하는 기능을 이벤트 기반 비동기 워커로 구현했다. 로컬 테스트에서 1,000,052건을 27.1419초에 저장하고 실제 건수를 확인했다. 하지만 프로세스 강제 종료 시 예외 처리조차 실행되지 않아 작업이 PROCESSING에 남고, 메모리의 실행 흐름이 사라지는 복구 공백이 있었다. 대상이 300만·500만 명으로 증가하면서 처리 속도뿐 아니라 중단 후 재개와 실행 이력 관리가 필요해졌다. 300만·500만 건 처리 시간은 아직 이 문서에서 검증하지 않았다.

### Analysis
기존 구현에는 이미 ID 커서 기반 1,000명 단위 조회, JDBC batch INSERT, 진행 상태, 중복 방지 유니크 제약이 있었다. 따라서 문제를 단순히 대량 INSERT 성능으로 정의하지 않았다. 실행 이력·트랜잭션 경계·재시작 상태를 직접 관리하는 부담과 강제 종료된 작업을 식별하고 복구하는 절차가 핵심이었다. 스케줄러는 시간에 따른 실행 도구일 뿐 이 문제를 해결하지 않는다.

### Action
Boot 4.0.3이 관리하는 Spring Batch 6.0.2와 JDBC JobRepository를 도입했다. 관리자 API와 AFTER_COMMIT 비동기 이벤트는 시작 경로로 유지하고, 직접 while 루프를 돌던 워커를 JobOperator 호출로 교체했다. broadcastId만 Job 식별 파라미터로 사용해 재시도가 같은 JobInstance에 연결된다.

Spring Batch의 chunk(1000) Step을 사용한다. StepScope JdbcPagingItemReader가 ACTIVE 회원 ID를 정렬해 읽고 ExecutionContext에 체크포인트를 저장한다. ItemWriter는 JDBC 알림 저장과 도메인 진행 건수 갱신을 수행한다. 청크 반복, 트랜잭션, 읽기 위치 저장 및 복원은 Spring Batch가 담당한다. 기존 반복 Tasklet, 수동 ID 조회 메서드, JobSnapshot은 삭제했다. NotificationBroadcastJobLauncher는 Job 시작만 담당한다. last_processed_user_id 필드는 기존 DB의 NOT NULL 스키마와 호환하기 위해 0으로만 초기화하며 처리·재시작에 사용하지 않는다.

중복 이벤트는 도메인 작업의 비관적 잠금과 PENDING 상태 검사로 차단한다. 기존 (공지 ID, 회원 ID) 유니크 제약을 유지한다. INSERT IGNORE가 중복 외 오류도 숨길 수 있으므로, 청크 전체의 알림 존재 여부를 검증한 뒤에만 청크를 커밋한다. 일반 실패는 기존 retry API, 강제 종료는 운영자가 명시적으로 활성화하는 단일 인스턴스 복구 모드로 구분한다.

### Result
구현 및 자동 테스트에서 실패한 청크의 INSERT 롤백, 동일 JobInstance 재시작, 완료 건수 및 중복 방지를 검증한다. 도입 후 500만 건 처리 시간, 실제 MySQL kill -9 복구 결과는 별도 실측 전까지 성과로 주장하지 않는다. Spring Batch가 더 빠르다고 가정하지 않는다. 이번 목표는 복구 가능성과 실행 이력 표준화다.

## 최초 실행
이 버전은 Reader의 ExecutionContext를 재시작 기준으로 사용한다. 예전 수동 워커/Tasklet으로 시작한 미완료 작업은 이 버전에서 재개하지 않는다. 기존 버전에서 완료시킨 뒤 전환하고 새 공지 ID로 테스트한다. 기존 Tasklet 체크포인트를 새 Reader로 변환하는 마이그레이션은 포함하지 않았다.

1. 기존 서버/공지 작업을 중지한다. 기존 DB와 사용자 데이터는 유지한다.
2. Workbench에서 대상 로컬 DB를 선택한 뒤 `docs/sql/spring-batch-6.0.2-mysql.sql`을 한 번 실행한다. 공식 6.0.2 JAR의 스키마이며 BATCH_* 테이블이 없는 DB용이다. 기존 Batch 테이블이 있다면 삭제/덮어쓰지 말고 버전별 마이그레이션을 검토한다.
3. `ddl-auto=create`를 사용하지 않는다. 일반 앱 스키마와 기존 유니크 제약이 있어야 한다.
4. 평소처럼 서버를 실행한다. `spring.batch.job.enabled=false`이므로 서버 시작만으로 새 공지를 발송하지 않는다. 운영의 자동 DDL 실행을 막기 위해 Batch initialize-schema는 never다. 테스트만 embedded H2 스키마를 자동 생성한다.
5. 기존 POST /admin/notifications/broadcast, GET /admin/notifications/broadcasts/{id}, POST /admin/notifications/broadcasts/{id}/retry를 그대로 사용한다. 인증 방식도 동일하다.

## 예외 실패와 강제 종료 복구
일반 예외: Batch 실행과 도메인 작업이 FAILED가 된다. 기존 관리자 retry API를 호출하면 같은 broadcastId로 새 JobExecution이 생성되며 마지막 커밋된 사용자 ID 이후부터 처리한다.

강제 종료: 이전 프로세스가 완전히 종료됐고 같은 DB를 사용하는 다른 공지 워커가 없음을 확인한다. **단 하나의 서버에만** 다음 실행 인자를 넣어 재기동한다.

```text
--app.notification-batch.recover-on-startup=true
```

복구 모드는 PENDING/PROCESSING 작업을 찾아 Batch 실행이 실행 중 상태로 남았다면 JobOperator.recover로 복구한 뒤 다시 시작한다. DB에 요청은 저장됐지만 이벤트를 실행하기 전 죽은 PENDING 작업도 포함한다. 이미 Batch COMPLETED인데 도메인 상태만 남았다면 완료 상태를 맞춘다. 일반 FAILED 작업은 관리자 retry로 처리한다.

이 설정은 기본적으로 꺼져 있다. 다음 정상 실행에서는 제거한다. 다중 인스턴스가 동작하는 중이거나 롤링 배포 중에 켜면 안 된다. 분산 리더 선출/리스/자동 장애 감지를 구현한 것은 아니다.

## 수동 장애 실험
1. 테스트 동안 회원 가입/상태 변경/삭제를 멈추고 ACTIVE 수를 기록한다.
2. 공지 1건을 요청하고 진행 중 processed_count가 증가하는지 확인한다.
3. 테스트 서버 PID를 정확히 확인해 해당 프로세스를 강제 종료한다.
4. 복구 모드로 재기동하고 같은 공지 ID가 COMPLETED가 되는지 확인한다.
5. 아래 SQL로 최종 건수, 고유 회원 수, 활성 회원 중 누락 수를 확인한다.

```sql
SET @broadcast_id = 1; -- 실제 작업 ID로 교체
SELECT status, total_target_count, processed_count, failure_reason
FROM notification_broadcast WHERE notification_broadcast_id = @broadcast_id;

SELECT COUNT(*) AS notifications, COUNT(DISTINCT user_id) AS unique_users
FROM notification WHERE notification_broadcast_id = @broadcast_id;

SELECT COUNT(*) AS missing_active_users FROM `user` u
WHERE u.status = 'ACTIVE' AND NOT EXISTS (
  SELECT 1 FROM notification n
  WHERE n.notification_broadcast_id = @broadcast_id AND n.user_id = u.user_id
);

SELECT e.JOB_EXECUTION_ID, e.STATUS, e.START_TIME, e.END_TIME, e.EXIT_MESSAGE
FROM BATCH_JOB_EXECUTION e
JOIN BATCH_JOB_EXECUTION_PARAMS p ON p.JOB_EXECUTION_ID = e.JOB_EXECUTION_ID
WHERE p.PARAMETER_NAME = 'broadcastId' AND p.PARAMETER_VALUE = CAST(@broadcast_id AS CHAR)
ORDER BY e.JOB_EXECUTION_ID;
```

처리 중 회원 상태가 바뀌면 시작 시 대상 수와 실제 처리 수는 달라질 수 있다. 현재 대상은 조회 시점 ACTIVE이며 발송 시작 시점 회원 스냅샷은 아니다. 정확한 시점 고정이 필요하면 수신자 스냅샷 테이블이 추가로 필요하다. SSE 수신 보장은 이번 DB 저장 작업의 범위가 아니다.

## 참고
- https://github.com/spring-projects/spring-batch/wiki/Spring-Batch-6.0-Migration-Guide
- https://docs.spring.io/spring-batch/reference/job/running.html
