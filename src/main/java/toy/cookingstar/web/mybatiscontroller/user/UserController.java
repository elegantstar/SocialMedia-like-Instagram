package toy.cookingstar.web.mybatiscontroller.user;

import java.io.IOException;
import java.net.MalformedURLException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import toy.cookingstar.domain.Member;
import toy.cookingstar.service.imagestore.ImageStoreService;
import toy.cookingstar.service.imagestore.ImageType;
import toy.cookingstar.mybatisservice.post.PostImageUrlParam;
import toy.cookingstar.mybatisservice.post.PostService;
import toy.cookingstar.service.post.StatusType;
import toy.cookingstar.service.user.GenderType;
import toy.cookingstar.mybatisservice.user.PwdUpdateParam;
import toy.cookingstar.mybatisservice.user.UserService;
import toy.cookingstar.mybatisservice.user.UserUpdateParam;
import toy.cookingstar.utils.HashUtil;
import toy.cookingstar.utils.formybatis.PagingVO;
import toy.cookingstar.utils.formybatis.SessionUtils;
import toy.cookingstar.web.argumentresolver.Login;
import toy.cookingstar.web.controller.user.form.InfoUpdateForm;
import toy.cookingstar.web.controller.user.form.PwdUpdateForm;
import toy.cookingstar.web.controller.validator.PwdValidator;

@Slf4j
//@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final PwdValidator pwdValidator;
    private final PostService postService;
    private final ImageStoreService imageStoreService;

    /**
     * ?????? ?????????
     */
    @GetMapping("/user/{userId}")
    public String userPage(@PathVariable String userId, @Login Member loginUser, Model model) {

        // ?????? ?????? userId??? ???????????? ????????? ????????? ?????? ??? ????????? ???????????? -> ???????????? ????????? ?????? ?????? exception ?????? ?????????.
        Member userPageInfo = userService.getUserInfo(userId);

        if (userPageInfo == null) {
            return "error-page/404";
        }

        model.addAttribute("userPageInfo", userPageInfo);

        // ?????? ?????? userId??? ????????? ????????? userId??? ????????? myPage???
        if (StringUtils.equals(userId, loginUser.getUserId())) {
            return "redirect:/myPage";
        }

        model.addAttribute("userPageInfo", userPageInfo);

        int totalPost = postService.countPosts(userPageInfo.getId());
        model.addAttribute("totalPost", totalPost);

        PostImageUrlParam postImageUrls = getPostImageUrls(userPageInfo, totalPost, StatusType.POSTING);
        if (postImageUrls == null) {
            return "user/userPage";
        }
        model.addAttribute("imageUrls", postImageUrls.getImageUrls());
        model.addAttribute("postIds", postImageUrls.getPostIds());

        // userPage???
        return "user/userPage";
    }

    /**
     * ?????? ?????????
     */
    @GetMapping("/myPage")
    public String myPage(@Login Member loginUser, Model model) {

        Member userPageInfo = userService.getUserInfo(loginUser.getUserId());

        if (userPageInfo == null) {
            return "error-page/404";
        }
        model.addAttribute("userPageInfo", userPageInfo);

        int totalPost = postService.countPosts(userPageInfo.getId());
        model.addAttribute("totalPost", totalPost);

        //TODO: Page??? ???????????? ?????? ?????? currentPageNo, postsPerPage, countPages??? Front?????? ?????? ????????? ??? ??????
        //????????? ????????? 1???????????? ???????????? ????????? ??????

        //TODO: getPostImageUrls??? ImageUrl??? postId??? ??????
        PostImageUrlParam postImageUrls = getPostImageUrls(userPageInfo, totalPost, StatusType.POSTING);
        if (postImageUrls == null) {
            return "user/myPage";
        }
        model.addAttribute("imageUrls", postImageUrls.getImageUrls());
        model.addAttribute("postIds", postImageUrls.getPostIds());

        return "user/myPage";
    }

    @GetMapping("/myPage/edit")
    public String editForm(@Login Member loginUser, Model model) {
        Member userInfo = userService.getUserInfo(loginUser.getUserId());
        model.addAttribute("userInfo", userInfo);
        return "user/editForm";
    }

    @PostMapping("/myPage/edit")
    public String editInfo(@Validated @ModelAttribute("userInfo") InfoUpdateForm form,
                           BindingResult bindingResult,
                           @Login Member loginUser, HttpServletRequest request) throws IOException {

        if (userService.isNotAvailableEmail(loginUser.getUserId(), form.getEmail())) {
            bindingResult.reject("edit.email.notAvailable");
        }

        if (bindingResult.hasErrors()) {
            log.error("errors={}", bindingResult);
            return "user/editForm";
        }

        UserUpdateParam userUpdateParam = new UserUpdateParam(loginUser.getUserId(), form.getNickname(),
                                                              form.getIntroduction(), form.getEmail(),
                                                              form.getGender());

        Member updatedUser = userService.updateInfo(userUpdateParam);

        if (updatedUser == null) {
            return "error-page/404";
        }

        // Session member update
        SessionUtils.refreshMember(userService.getUserInfo(loginUser.getUserId()), request);

        return "redirect:/myPage/edit";
    }

    @GetMapping("/myPage/password/change")
    public String passwordForm(@ModelAttribute("userPwdInfo") PwdUpdateForm form, @Login Member loginUser,
                               Model model) {
        Member userInfo = userService.getUserInfo(loginUser.getUserId());
        model.addAttribute("userInfo", userInfo);
        return "user/pwdForm";
    }

    @PostMapping("/myPage/password/change")
    public String updatePwd(@Validated @ModelAttribute("userPwdInfo") PwdUpdateForm form,
                            BindingResult bindingResult, @Login Member loginUser, HttpServletRequest request) {

        if (isWrongPwd(form, loginUser)) {
            bindingResult.reject("error.wrong.password");
        }

        pwdValidator.validEquality(form.getNewPassword1(), form.getNewPassword2(), bindingResult);

        if (bindingResult.hasErrors()) {
            log.error("errors={}", bindingResult);
            return "user/pwdForm";
        }

        PwdUpdateParam pwdUpdateParam = new PwdUpdateParam(loginUser.getUserId(), form.getCurrentPwd(),
                                                           form.getNewPassword1(), form.getNewPassword2());

        Member updatedUser = userService.updatePwd(pwdUpdateParam);

        if (updatedUser == null) {
            return "error-page/404";
        }

        // Session update
        SessionUtils.refreshMember(userService.getUserInfo(loginUser.getUserId()), request);

        return "redirect:/myPage/password/updated";
    }

    @GetMapping("/myPage/password/updated")
    public String updatedPwd(@Login Member loginUser, Model model) {
        Member userInfo = userService.getUserInfo(loginUser.getUserId());
        model.addAttribute("userInfo", userInfo);

        return "user/pwdUpdatedPage";
    }

    @GetMapping("/myPage/private")
    public String privatePage(@Login Member loginUser, Model model) {

        Member userPageInfo = userService.getUserInfo(loginUser.getUserId());

        if (userPageInfo == null) {
            return "error-page/404";
        }
        model.addAttribute("userPageInfo", userPageInfo);

        int totalPost = postService.countPosts(userPageInfo.getId());
        model.addAttribute("totalPost", totalPost);

        //getPostImageUrls??? ImageUrl??? postId??? ??????
        PostImageUrlParam postImageUrls = getPostImageUrls(userPageInfo, totalPost, StatusType.PRIVATE);

        if (postImageUrls == null) {
            return "user/privatePage";
        }

        model.addAttribute("imageUrls", postImageUrls.getImageUrls());
        model.addAttribute("postIds", postImageUrls.getPostIds());

        return "user/privatePage";
    }

    // ????????? ?????? ????????? ??????
    private PostImageUrlParam getPostImageUrls(Member userPageInfo, int totalPost, StatusType statusType) {
        int currentPageNo = 1;
        int postsPerPage = 12;
        int countPages = 1;

        PagingVO pagingVO = new PagingVO(totalPost, currentPageNo, countPages, postsPerPage);

        return postService.getUserPagePostImages(userPageInfo.getUserId(), pagingVO.getStart(),
                                                 pagingVO.getEnd(), statusType);
    }

    /**
     * ????????? ????????? ????????????
     */
    @ResponseBody
    @GetMapping("/profile/{imageUrl}")
    public Resource userProfileImage(@PathVariable String imageUrl, HttpServletResponse response)
            throws MalformedURLException {
        response.setHeader("Cache-Control", "max-age=60");
        return new UrlResource("https://d9voyddk1ma4s.cloudfront.net/" + imageStoreService
                .getFullPath(ImageType.PROFILE, imageUrl));
    }

    /**
     * ????????? ????????? ????????????
     */
    @ResponseBody
    @GetMapping("/image/{imageUrl}")
    public Resource userPageImage(@PathVariable String imageUrl, HttpServletResponse response)
            throws MalformedURLException {
        response.setHeader("Cache-Control", "max-age=60");
        return new UrlResource("https://d9voyddk1ma4s.cloudfront.net/" + imageStoreService
                .getFullPath(ImageType.POST, imageUrl));
    }

    @ModelAttribute("genderTypes")
    public GenderType[] genderTypes() {
        return GenderType.values();
    }

    private boolean isWrongPwd(PwdUpdateForm form, Member loginUser) {
        return !StringUtils.equals(loginUser.getPassword(),
                                   HashUtil.encrypt(form.getCurrentPwd() + loginUser.getSalt()));
    }
}
