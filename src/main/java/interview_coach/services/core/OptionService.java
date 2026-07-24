package interview_coach.services.core;

import interview_coach.dto.OptionUpdateDTO;
import interview_coach.entities.Option;
import interview_coach.exceptions.OptionNotFoundException;
import interview_coach.repositories.OptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OptionService {

    private final OptionRepository optionRepository;

    @Transactional
    public void createOption(Option option) {
        optionRepository.save(option);
    }

    @Transactional
    public void updateOption(Long optionId, OptionUpdateDTO optionUpdateDTO) {
        Option option = optionRepository.findById(optionId)
                .orElseThrow(() -> new OptionNotFoundException("Option not found with id: " + optionId));
        option.setCorrectOption(optionUpdateDTO.correctOption());
        option.setOption1(optionUpdateDTO.option1());
        option.setOption2(optionUpdateDTO.option2());
        option.setOption3(optionUpdateDTO.option3());
        option.setOption4(optionUpdateDTO.option4());
        optionRepository.save(option);
    }

    @Transactional
    public void deleteOption(Long optionId) {
        optionRepository.deleteById(optionId);
    }
}
