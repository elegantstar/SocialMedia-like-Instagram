package toy.cookingstar.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import toy.cookingstar.entity.Post;
import toy.cookingstar.entity.PostComment;

import java.util.List;

public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

    @Query("select c from PostComment c where c.post.id = :postId and c.parentComment.id is null")
    Slice<PostComment> findComments(@Param("postId") Long postId, Pageable pageable);

    @Query("select c from PostComment c where c.post.id = :postId and c.parentComment.id= :parentCommentId")
    Slice<PostComment> findNestedComments(@Param("postId") Long postId,
                                    @Param("parentCommentId") Long parentCommentId,
                                    Pageable pageable);

    Boolean existsNestedCommentsByParentCommentId(Long parentCommentId);

    int countByPost(Post post);

    List<PostComment> findByPost(Post post, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PostComment c where c.post = :post")
    void deleteAllByPost(@Param("post") Post post);
}
