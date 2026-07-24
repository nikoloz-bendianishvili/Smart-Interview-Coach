package interview_coach.services.core;

import interview_coach.dto.TopicUpdateDTO;
import interview_coach.entities.Topic;
import interview_coach.exceptions.TopicAlreadyExistsException;
import interview_coach.exceptions.TopicNotFoundException;
import interview_coach.repositories.QuestionRepository;
import interview_coach.repositories.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TopicService {

    private final TopicRepository topicRepository;
    private final QuestionRepository questionRepository;

    @Transactional
    public void createTopic(Topic topic) {
        if (topicRepository.existsTopicByTopicName((topic.getTopicName()))) {
            throw new TopicAlreadyExistsException("Topic already exists: " + topic.getTopicName());
        }
        topicRepository.save(topic);
    }

    @Transactional
    public void deleteTopic(Long topicId) {
        if (questionRepository.findByTopicId((topicId)) != null) {
            throw new IllegalStateException("Cannot delete topic with existing questions attached.");
        }
        topicRepository.deleteById(topicId);
    }

    @Transactional
    public void updateTopic(Long topicId, TopicUpdateDTO topicUpdateDTO) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new TopicNotFoundException("Topic not found with id: " + topicId));

        topic.setTopicName(topicUpdateDTO.topicName());
        topic.setDescription(topicUpdateDTO.description());
        topicRepository.save(topic);
    }

    public List<Topic> getAllTopics() {
        return topicRepository.findAll();
    }

}
