# 환경 설정

## 프로필

로컬 실행은 `local` 프로필을 사용합니다. 자세한 실행 절차는 [개발 환경과 로컬 실행](development.md)을 참고하세요.

운영 환경 변수의 형식은 [`.env.example`](../.env.example)을 참고하세요. 해당 파일의 값은 예시이며 Spring이 자동으로 읽지 않습니다. 셸, IDE, 배포 플랫폼에서 실제 값을 설정해야 합니다.

## DataGSM 환경 변수

기본값은 `DATAGSM_ENABLED=false`입니다. DataGSM 로그인 설정과 필요한 환경 변수는 [DataGSM 인증](datagsm-authentication.md)을 참고하세요.
