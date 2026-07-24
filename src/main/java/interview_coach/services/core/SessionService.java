package interview_coach.services.core;

import interview_coach.dto.SessionDTO;
import interview_coach.entities.*;
import interview_coach.enums.InteractionMode;
import interview_coach.enums.QuestionType;
import interview_coach.enums.SessionStatus;
import interview_coach.enums.SessionType;
import interview_coach.exceptions.InsufficientQuestionsException;
import interview_coach.exceptions.SessionNotFoundException;
import interview_coach.repositories.AttemptRepository;
import interview_coach.repositories.QuestionRepository;
import interview_coach.repositories.SessionQuestionRepository;
import interview_coach.repositories.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final AttemptRepository attemptRepository;
    private final SessionQuestionRepository sessionQuestionRepository;

    private static final double OPEN_ENDED_RATIO = 0.75;

    public Session getSessionById(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("Session not found with id: " + sessionId));
    }

    @Transactional
    public Session startSession(SessionDTO sessionDTO) {
        Session session = Session.builder()
                .user(sessionDTO.user())
                .topic(sessionDTO.topic())
                .sessionType(sessionDTO.sessionType())
                .questionType(sessionDTO.questionType())
                .interactionMode(sessionDTO.interactionMode())
                .status(SessionStatus.IN_PROGRESS)
                .numOfQuestions(sessionDTO.numOfQuestions())
                .timeLimitMinutes(sessionDTO.timeLimitInMinutes())
                .build();
        sessionRepository.save(session);

        List<Question> questions = selectQuestionsForSession(session);

        List<SessionQuestion> sessionQuestions = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            sessionQuestions.add(SessionQuestion.builder()
                    .session(session)
                    .question(questions.get(i))
                    .orderIndex(i + 1)
                    .build());
        }
        sessionQuestionRepository.saveAll(sessionQuestions);
        return session;
    }

    @Transactional
    public void completeSession(Long sessionId) {
        Session session = getSessionById(sessionId);
        if (session != null) {
            session.setStatus(SessionStatus.COMPLETED);
            session.setEndTime(java.time.LocalDateTime.now());
            session.setTotalScore(calculateTotalScore(session));
            sessionRepository.save(session);
        }
    }

    public List<Session> getSessionsByUserDesc(Long userId) {
        return sessionRepository.findByUserIdOrderByStartTimeDesc(userId);
    }

    public List<Session> getSessionsByUserAndStatus(Long userId, SessionStatus status) {
        return sessionRepository.findByUserIdAndStatus(userId, status);
    }


    private List<Question> selectQuestionsForSession(Session session) {
        return switch (session.getSessionType()) {
            case CUSTOM_PRACTICE -> selectQuestionsForCustomPractice(
                    session.getTopic().getId(),
                    session.getQuestionType(),
                    session.getNumOfQuestions()
            );

            case REAL_INTERVIEW -> selectMixedQuestions(
                    calculateQuestionCountFromTime(session.getTimeLimitMinutes())
            );

            case FREE_MOCK -> {
                validateFreeMockInput(session);
                int total = session.getNumOfQuestions() != null
                        ? session.getNumOfQuestions()
                        : calculateQuestionCountFromTime(session.getTimeLimitMinutes());
                yield selectMixedQuestions(total);
            }
        };
    }

    private List<Question> selectQuestionsForCustomPractice(Long topicId, QuestionType questionType, int numOfQuestions) {
        long available = questionRepository.countByTopicIdAndQuestionType(topicId, questionType);

        if (available < numOfQuestions) {
            throw new InsufficientQuestionsException(
                    "Only " + available + " questions available for this topic and type, but " + numOfQuestions + " were requested."
            );
        }

        return questionRepository.findRandomByTopicAndType(topicId, questionType.name(), numOfQuestions);
    }

    private void validateFreeMockInput(Session session) {
        boolean hasCount = session.getNumOfQuestions() != null;
        boolean hasTimeLimit = session.getTimeLimitMinutes() != null;

        if (hasCount == hasTimeLimit) {
            throw new IllegalArgumentException("Free Mock requires exactly one of numOfQuestions or timeLimitMinutes.");
        }
    }

    private List<Question> selectMixedQuestions(int totalQuestions) {
        int openEndedCount = (int) Math.round(totalQuestions * OPEN_ENDED_RATIO);
        int codingCount = totalQuestions - openEndedCount;

        long availableCoding = questionRepository.countByQuestionType(QuestionType.CODING);
        long availableOpenEnded = questionRepository.countByQuestionType(QuestionType.OPEN_ENDED);

        if (availableCoding < codingCount || availableOpenEnded < openEndedCount) {
            throw new InsufficientQuestionsException(
                    "Not enough questions available. Requested " + codingCount + " coding (have " + availableCoding +
                            ") and " + openEndedCount + " open-ended (have " + availableOpenEnded + ")."
            );
        }

        List<Question> questions = new ArrayList<>();
        questions.addAll(questionRepository.findRandomByType(QuestionType.CODING.name(), codingCount));
        questions.addAll(questionRepository.findRandomByType(QuestionType.OPEN_ENDED.name(), openEndedCount));
        Collections.shuffle(questions);
        return questions;
    }


    private int calculateQuestionCountFromTime(int timeLimitMinutes) {
        Double avgCodingSeconds = questionRepository.findAverageTimeLimitByType(QuestionType.CODING);
        Double avgOpenEndedSeconds = questionRepository.findAverageTimeLimitByType(QuestionType.OPEN_ENDED);

        if (avgCodingSeconds == null || avgOpenEndedSeconds == null) {
            throw new InsufficientQuestionsException("Not enough questions in the database to estimate session length.");
        }

        double avgSecondsPerQuestion =
                (OPEN_ENDED_RATIO * avgOpenEndedSeconds) +
                        ((1 - OPEN_ENDED_RATIO) * avgCodingSeconds);

        double timeLimitSeconds = timeLimitMinutes * 60.0;
        return Math.max(1, (int) Math.round(timeLimitSeconds / avgSecondsPerQuestion));
    }


    private int calculateTotalScore(Session session) {
        List<Attempt> attempts = attemptRepository.findBySessionQuestion_Session_Id(session.getId());

        return attempts.stream()
                .filter(attempt -> !attempt.isWasSkipped())
                .mapToInt(attempt -> attempt.getScore() != null ? attempt.getScore() : 0)
                .sum();
    }

    public List<SessionQuestion> getSessionQuestions(Long sessionId) {
        return sessionQuestionRepository.findBySessionIdOrderByOrderIndexAsc(sessionId);
    }

}
