package com.sonbadrim_server.domain.user.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 관리 컨트롤러
 */
@Tag(name = "사용자", description = "사용자 정보 및 설정 관리 API")
@RestController
@RequestMapping("/users")
@Slf4j
public class UserController {

}
