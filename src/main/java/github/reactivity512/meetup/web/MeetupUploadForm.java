package github.reactivity512.meetup.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

public class MeetupUploadForm {

    @NotBlank(message = "Укажите название митинга")
    @Size(max = 200, message = "Название не должно превышать 200 символов")
    private String title;

    private MultipartFile file;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile file) {
        this.file = file;
    }
}
