package github.reactivity512.meetup.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class QuestionForm {

    @NotBlank(message = "Введите вопрос")
    @Size(max = 4000, message = "Вопрос слишком длинный")
    private String question;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
