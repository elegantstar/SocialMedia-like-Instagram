package toy.cookingstar.entity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static javax.persistence.FetchType.*;

@Entity
@Getter
@Table(name = "post_comment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostComment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "parent_comment_id")
    private PostComment parentComment;

    private String content;

    @Column(name = "created_date", insertable = false, updatable = false)
    private LocalDateTime createdDate;

    @Column(name = "updated_date", insertable = false, updatable = false)
    private LocalDateTime updatedDate;

    @Column(name = "deleted_date")
    private LocalDateTime deletedDate;

    public static PostComment createComment(Member member, Post post, PostComment parentComment, String content) {
        PostComment postComment = new PostComment();
        postComment.member = member;
        postComment.post = post;
        postComment.parentComment = parentComment;
        postComment.content = content;
        return postComment;
    }

    public void deleteComment() {
        this.content = null;
        this.deletedDate = LocalDateTime.now();
    }
}
