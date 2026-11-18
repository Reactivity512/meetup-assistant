package github.reactivity512.meetup.web;

import github.reactivity512.meetup.domain.Meetup;
import github.reactivity512.meetup.service.MeetupNotFoundException;
import github.reactivity512.meetup.service.MeetupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meetups")
public class MeetupApiController {

    private final MeetupService meetupService;

    public MeetupApiController(MeetupService meetupService) {
        this.meetupService = meetupService;
    }

    public record MeetupStatusResponse(
            Long id,
            String status,
            String statusLabel,
            String badgeClass,
            boolean busy,
            boolean transcriptAvailable,
            int transcriptChars,
            String processingError) {
    }

    /**
     * Used by the UI to refresh processing status without reloading the whole page.
     */
    @GetMapping("/{id}/status")
    public ResponseEntity<MeetupStatusResponse> status(@PathVariable Long id) {
        try {
            Meetup meetup = meetupService.get(id);
            MeetupStatusResponse response = new MeetupStatusResponse(
                    meetup.getId(),
                    meetup.getStatus().name(),
                    meetup.getStatus().getLabel(),
                    meetup.getStatus().getBadgeClass(),
                    meetup.getStatus().isBusy(),
                    meetup.getTranscript() != null && !meetup.getTranscript().isBlank(),
                    meetup.getTranscriptChars(),
                    meetup.getProcessingError());
            return ResponseEntity.ok(response);
        } catch (MeetupNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
