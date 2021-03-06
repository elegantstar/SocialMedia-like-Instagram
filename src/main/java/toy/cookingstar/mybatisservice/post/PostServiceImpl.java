package toy.cookingstar.mybatisservice.post;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import toy.cookingstar.domain.Member;
import toy.cookingstar.domain.Post;
import toy.cookingstar.domain.PostImage;
import toy.cookingstar.domain.PostWithImage;

import toy.cookingstar.mapper.MemberMapper;
import toy.cookingstar.mapper.PostMapper;
import toy.cookingstar.service.post.StatusType;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostServiceImpl implements PostService {

    private final PostMapper postMapper;
    private final MemberMapper memberMapper;

    @Override
    @Transactional
    public void createPost(PostCreateParam postCreateParam) {

        String userId = postCreateParam.getUserId();
        Member user = memberMapper.findByUserId(userId);

        Post post = Post.builder()
                        .memberId(user.getId())
                        .content(postCreateParam.getContent())
                        .status(postCreateParam.getStatus())
                        .build();

        // post 테이블에 포스트 데이터 생성
        postMapper.create(post);

        // postImage 테이블에 업로드한 이미지 데이터 생성
        List<String> storedImages = postCreateParam.getStoredImages();
        for (String storedImage : storedImages) {
            PostImage postImage = PostImage.builder()
                                           .postId(post.getId())
                                           .url(storedImage)
                                           .priority(storedImages.indexOf(storedImage) + 1)
                                           .build();

            postMapper.saveImage(postImage);
        }
    }

    @Override
    public Post findById(Long postId) {
        return postMapper.findByPostId(postId);
    }

    @Override
    public PostImageUrlParam getUserPagePostImages(String userId, int start, int end, StatusType statusType) {

        Member user = memberMapper.findByUserId(userId);

        if (user == null) {
            return null;
        }

        return getPostImages(postMapper.findPostWithImages(user.getId(), statusType, start, end));
    }

    private PostImageUrlParam getPostImages(List<PostWithImage> postWithImages) {
        if (CollectionUtils.isEmpty(postWithImages)) {
            return null;
        }

        List<String> imageUrls = postWithImages.stream().map(PostWithImage::getImages).flatMap(Collection::stream)
                                               .collect(Collectors.toList()).stream().map(PostImage::getUrl)
                                               .collect(Collectors.toList());

        List<String> postIds = postWithImages.stream().map(Post::getId)
                                              .map(Object::toString).collect(Collectors.toList());

        return new PostImageUrlParam(imageUrls, postIds);
    }

    @Override
    public PostWithImage getPostInfo(Long postId) {

        PostWithImage postInfo = postMapper.findPostInfo(postId);

        if (postInfo == null || CollectionUtils.isEmpty(postInfo.getImages())) {
            return null;
        }
        return postInfo;
    }

    @Override
    public int countPosts(Long memberId) {
        return postMapper.countPosts(memberId);
    }

    @Override
    @Transactional
    public void deletePost(String userId, Long postId) {
        postMapper.deletePostImages(postId);
        postMapper.deletePost(postId);
        log.info("DELETE POST: userId=[{}], deletedPostId=[{}]", userId, postId);
    }

    @Override
    @Transactional
    public void updatePost(String userId, Long id, String content, StatusType status) {
        postMapper.updatePost(id, content, status);
        log.info("UPDATE POST: userId=[{}], updatedPostId=[{}]", userId, id);
    }

    @Override
    public List<PostWithImage> getTemporaryStorage(Long memberId, StatusType statusType, int start, int end) {

        Member user = memberMapper.findById(memberId);

        if (user == null) {
            return null;
        }

        return postMapper.findPostWithImages(memberId, statusType, start, end);
    }
}
