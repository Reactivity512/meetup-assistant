package github.reactivity512.meetup.repository;

import java.time.Instant;
import java.util.List;

import github.reactivity512.meetup.domain.Meetup;
import github.reactivity512.meetup.domain.MeetupStatus;
import github.reactivity512.meetup.domain.MeetupSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MeetupRepository extends JpaRepository<Meetup, Long> {

    @Query("""
            select new github.reactivity512.meetup.domain.MeetupSummary(
                m.id, m.title, m.status, m.createdAt, length(m.transcript), m.processingError)
            from Meetup m
            order by m.createdAt desc
            """)
    List<MeetupSummary> findAllSummaries();

    List<Meetup> findByCreatedAtBeforeAndAudioPathIsNotNull(Instant cutoff);

    List<Meetup> findByStatusInOrderByCreatedAtAsc(List<MeetupStatus> statuses);
}
