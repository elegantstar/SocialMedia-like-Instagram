package toy.cookingstar.service.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
public class UserUpdateParam {

    private String userId;
    private String nickname;
    private String introduction;
    private String email;
    private String gender;
    private String profileImage;

}