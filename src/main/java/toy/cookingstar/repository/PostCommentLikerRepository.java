package toy.cookingstar.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import toy.cookingstar.entity.Member;
import toy.cookingstar.entity.PostComment;
import toy.cookingstar.entity.PostCommentLiker;

public interface PostCommentLikerRepository extends JpaRepository<PostCommentLiker, Long> {

    int countByPostComment(PostComment postComment);

    @Query("select l from PostCommentLiker l where l.postComment = :postComment")
    Slice<PostCommentLiker> findLikersByPostComment(@Param("postComment") PostComment postComment, Pageable pageable);

    boolean existsByMemberAndPostComment(Member member, PostComment postComment);

    PostCommentLiker findByMemberAndPostComment(Member member, PostComment postComment);
}
