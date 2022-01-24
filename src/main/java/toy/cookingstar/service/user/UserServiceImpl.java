package toy.cookingstar.service.user;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import toy.cookingstar.domain.Member;
import toy.cookingstar.repository.MemberRepository;
import toy.cookingstar.utils.HashUtil;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final MemberRepository memberRepository;

    @Override
    public Member getUserInfo(String userId) {

        return memberRepository.findByUserId(userId);

    }

    @Override
    public boolean isNotAvailableEmail(String userId, String email) {

        Member foundMember = memberRepository.findByEmail(email);

        if (foundMember != null) {
            if (!userId.equals(foundMember.getUserId())) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Transactional
    public Member updateInfo(UserUpdateParam userUpdateParam) {

        String userId = userUpdateParam.getUserId();

        // userId가 DB에 존재하는지 검증
        if (isNotJoinedUser(userId)) {
            return null;
        }

        // 회원 정보 업데이트
        memberRepository.updateInfo(userUpdateParam);

        return getUserInfo(userId);
    }

    @Override
    @Transactional
    public Member updatePwd(PwdUpdateParam pwdUpdateParam) {

        String userId = pwdUpdateParam.getUserId();

        // userId가 DB에 존재하는지 검증
        if (isNotJoinedUser(userId)) {
            return null;
        }

        // salt 생성
        String newSalt = UUID.randomUUID().toString().substring(0, 16);

        // hashing - sha-256
        String newPassword = HashUtil.encrypt(pwdUpdateParam.getNewPassword1() + newSalt);

        // 회원 비밀번호 업데이트
        memberRepository.updatePwd(userId, newPassword, newSalt);

        return getUserInfo(userId);
    }

    private boolean isNotJoinedUser(String userId) {
        return memberRepository.findByUserId(userId) == null;
    }

}
