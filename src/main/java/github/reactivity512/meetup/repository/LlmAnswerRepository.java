package github.reactivity512.meetup.repository;

import java.util.List;

import github.reactivity512.meetup.domain.LlmAnswer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmAnswerRepository extends JpaRepository<LlmAnswer, Long> {

    List<LlmAnswer> findByMeetupIdOrderByCreatedAtDesc(Long meetupId, Pageable pageable);

    void deleteByMeetupId(Long meetupId);
}
