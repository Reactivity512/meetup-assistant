package github.reactivity512.meetup.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import github.reactivity512.meetup.assistant.AssistantException;
import github.reactivity512.meetup.assistant.AssistantService;
import github.reactivity512.meetup.config.AssistantProperties;
import github.reactivity512.meetup.domain.LlmAnswer;
import github.reactivity512.meetup.domain.Meetup;
import github.reactivity512.meetup.service.MeetupService;
import github.reactivity512.meetup.service.TranscriptionCoordinator;
import github.reactivity512.meetup.storage.StorageException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/meetups")
public class MeetupController {

    private static final Logger log = LoggerFactory.getLogger(MeetupController.class);

    private final MeetupService meetupService;
    private final AssistantService assistantService;
    private final TranscriptionCoordinator transcriptionCoordinator;
    private final AssistantProperties assistantProperties;

    public MeetupController(MeetupService meetupService,
                            AssistantService assistantService,
                            TranscriptionCoordinator transcriptionCoordinator,
                            AssistantProperties assistantProperties) {
        this.meetupService = meetupService;
        this.assistantService = assistantService;
        this.transcriptionCoordinator = transcriptionCoordinator;
        this.assistantProperties = assistantProperties;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("meetups", meetupService.listSummaries());
        return "meetups/list";
    }

    @GetMapping("/new")
    public String uploadForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new MeetupUploadForm());
        }
        return "meetups/new";
    }

    @PostMapping
    public String upload(@Valid @ModelAttribute("form") MeetupUploadForm form,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (form.getFile() == null || form.getFile().isEmpty()) {
            bindingResult.rejectValue("file", "file.empty", "Выберите аудиофайл митинга");
        }
        if (bindingResult.hasErrors()) {
            return "meetups/new";
        }

        Meetup meetup;
        try {
            meetup = meetupService.create(form.getTitle(), form.getFile());
        } catch (IllegalArgumentException | StorageException e) {
            bindingResult.reject("upload", e.getMessage());
            return "meetups/new";
        }

        enqueue(meetup, redirectAttributes);
        redirectAttributes.addFlashAttribute("success",
                "Файл загружен. Распознавание началось — страница обновится автоматически.");
        return "redirect:/meetups/" + meetup.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Meetup meetup = meetupService.get(id);
        List<LlmAnswer> answers = meetupService.answers(id, assistantProperties.getHistoryLimit());
        model.addAttribute("meetup", meetup);
        model.addAttribute("answers", answers);
        model.addAttribute("audioAvailable", meetupService.isAudioAvailable(meetup));
        model.addAttribute("maxQuestionLength", assistantProperties.getMaxQuestionLength());
        if (!model.containsAttribute("questionForm")) {
            model.addAttribute("questionForm", new QuestionForm());
        }
        return "meetups/detail";
    }

    @PostMapping("/{id}/assistant")
    public String ask(@PathVariable Long id,
                      @Valid @ModelAttribute("questionForm") QuestionForm form,
                      BindingResult bindingResult,
                      RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("assistantError",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            redirectAttributes.addFlashAttribute("questionForm", form);
            return "redirect:/meetups/" + id;
        }
        try {
            assistantService.ask(id, form.getQuestion());
            redirectAttributes.addFlashAttribute("assistantSuccess", "Ответ получен и сохранён в истории.");
        } catch (AssistantException e) {
            redirectAttributes.addFlashAttribute("assistantError", e.getMessage());
            redirectAttributes.addFlashAttribute("questionForm", form);
        } catch (Exception e) {
            log.error("Unexpected error while answering question for meetup {}", id, e);
            redirectAttributes.addFlashAttribute("assistantError",
                    "Не удалось получить ответ: " + e.getMessage());
            redirectAttributes.addFlashAttribute("questionForm", form);
        }
        return "redirect:/meetups/" + id;
    }

    @PostMapping("/{id}/retry")
    public String retry(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Meetup meetup = meetupService.get(id);
        if (!meetupService.isAudioAvailable(meetup)) {
            redirectAttributes.addFlashAttribute("assistantError",
                    "Аудиофайл уже удалён по политике хранения — загрузите митинг заново.");
            return "redirect:/meetups/" + id;
        }
        meetupService.queueForRetry(id);
        enqueue(meetup, redirectAttributes);
        redirectAttributes.addFlashAttribute("success", "Распознавание запущено заново.");
        return "redirect:/meetups/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        meetupService.delete(id);
        redirectAttributes.addFlashAttribute("success", "Митинг удалён вместе с историей вопросов.");
        return "redirect:/meetups";
    }

    @GetMapping("/{id}/audio")
    public ResponseEntity<?> audio(@PathVariable Long id,
                                   @RequestHeader HttpHeaders headers) throws IOException {
        Meetup meetup = meetupService.get(id);
        if (!meetupService.isAudioAvailable(meetup)) {
            return ResponseEntity.notFound().build();
        }
        Path file = meetupService.audioPath(meetup);
        Resource resource = new FileSystemResource(file);
        long length = resource.contentLength();
        MediaType mediaType = resolveMediaType(meetup, resource);

        ContentDisposition disposition = ContentDisposition.inline()
                .filename(meetup.getAudioOriginalName() == null ? "audio" : meetup.getAudioOriginalName(),
                        StandardCharsets.UTF_8)
                .build();

        String rangeHeader = headers.getFirst(HttpHeaders.RANGE);
        if (rangeHeader == null) {
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .contentLength(length)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .body(resource);
        }
        try {
            List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
            if (ranges.isEmpty()) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header(HttpHeaders.CONTENT_RANGE, "bytes */" + length)
                        .build();
            }
            ResourceRegion region = ranges.get(0).toResourceRegion(resource);
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .body(region);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + length)
                    .build();
        }
    }

    private MediaType resolveMediaType(Meetup meetup, Resource resource) {
        String stored = meetup.getAudioContentType();
        if (stored != null && !stored.isBlank()) {
            try {
                return MediaType.parseMediaType(stored);
            } catch (IllegalArgumentException ignored) {
                // fall through to extension-based detection
            }
        }
        return MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
    }

    private void enqueue(Meetup meetup, RedirectAttributes redirectAttributes) {
        try {
            transcriptionCoordinator.processAsync(meetup.getId());
        } catch (TaskRejectedException e) {
            log.warn("Transcription queue is full; meetup {} cannot be processed", meetup.getId());
            meetupService.fail(meetup.getId(),
                    "Очередь распознавания переполнена. Нажмите «Распознать заново» через несколько минут.");
            redirectAttributes.addFlashAttribute("assistantError",
                    "Очередь распознавания переполнена. Попробуйте позже.");
        }
    }
}
