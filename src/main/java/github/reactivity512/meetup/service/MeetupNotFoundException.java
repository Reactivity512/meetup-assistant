package github.reactivity512.meetup.service;

public class MeetupNotFoundException extends RuntimeException {

    public MeetupNotFoundException(Long id) {
        super("Митинг с id=" + id + " не найден");
    }
}
