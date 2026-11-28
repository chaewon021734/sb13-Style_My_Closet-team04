package com.project.sb13ootbsteam04.auth.event;

import com.project.sb13ootbsteam04.auth.service.TemporaryPasswordMailService;
import com.project.sb13ootbsteam04.auth.service.TemporaryPasswordRecoveryService;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class TemporaryPasswordMailListener {

    private static final Logger log =
            LoggerFactory.getLogger(
                    TemporaryPasswordMailListener.class
            );

    private final TemporaryPasswordMailService mailService;
    private final TemporaryPasswordRecoveryService recoveryService;

    // DB 트랜잭션 커밋 성공 후 이메일 발송
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handle(TemporaryPasswordIssuedEvent event) {

        try {

            // 1. 임시 비밀번호 이메일 발송
            mailService.sendTemporaryPassword(
                    event.email(),
                    event.temporaryPassword()
            );

        } catch (MailException e) {

            // 2. SMTP 발송 실패
            // 이메일, 비밀번호, 예외 상세 내용은 로그에 남기지 않음
            log.warn(
                    "임시 비밀번호 이메일 발송에 실패했습니다."
            );

            try {

                // 3. 별도 트랜잭션에서 임시 비밀번호 복구
                recoveryService.recover(
                        event.email(),
                        event.temporaryPasswordHash(),
                        event.loginVersion()
                );

            } catch (Exception recoveryException) {

                // 4. 복구 실패 시에도 민감정보 출력 금지
                log.error(
                        "임시 비밀번호 발송 실패 후 복구 작업도 실패했습니다."
                );
            }
        }
    }
}
