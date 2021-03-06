package toy.cookingstar.service.login;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import toy.cookingstar.entity.Member;
import toy.cookingstar.repository.MemberRepository;
import toy.cookingstar.utils.HashUtil;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LoginService {

    private final MemberRepository memberRepository;

    public Member login(String userId, String password) {

        // 1.아이디 조회
        Member foundMember = memberRepository.findByUserId(userId);

        if (foundMember == null) {
            return null;
        }

        // 2. 패스워드와 조회한 아이디의 salt를 이용하여 hashing
        String requestPwd = HashUtil.encrypt(password + foundMember.getSalt());

        // 3. hashing 결과와 조회한 아이디의 패스워드가 같으면 Member 객체 반환, 다르면 null 반환
        return (StringUtils.equals(requestPwd, foundMember.getPassword()) ? foundMember : null);
    }
}
