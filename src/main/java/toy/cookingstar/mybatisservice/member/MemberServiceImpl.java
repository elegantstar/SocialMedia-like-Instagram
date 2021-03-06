package toy.cookingstar.mybatisservice.member;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import toy.cookingstar.domain.Member;
import toy.cookingstar.mapper.MemberMapper;
import toy.cookingstar.utils.HashUtil;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberServiceImpl implements MemberService {

    private final MemberMapper memberMapper;

    @Override
    @Transactional
    public Member saveMember(String userId, String password, String name, String email) {

        //1. 아이디 중복 검증
        if (idAlreadyExist(userId)) {
            return null;
        }

        //2. 이메일 중복 검증
        if (emailAlreadyExist(email)) {
            return null;
        }

        //3. salt 생성
        String salt = UUID.randomUUID().toString().substring(0, 16);

        //4. hashing - sha-256
        String encryptedPwd = HashUtil.encrypt(password + salt);

        //5. member 생성
        Member member = Member.builder()
                              .userId(userId)
                              .password(encryptedPwd)
                              .name(name)
                              .email(email)
                              .nickname(name)
                              .salt(salt)
                              .build();

        //6. member 저장
        memberMapper.save(member);

        return member;
    }

    private boolean emailAlreadyExist(String email) {
        return memberMapper.findByEmail(email) != null;
    }

    private boolean idAlreadyExist(String userId) {
        return memberMapper.findByUserId(userId) != null;
    }

}
