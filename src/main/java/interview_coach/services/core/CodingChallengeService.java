package interview_coach.services.core;


import interview_coach.dto.CodingChallengeUpdateDTO;
import interview_coach.entities.CodingChallenge;
import interview_coach.exceptions.CodingChallengeNotFound;
import interview_coach.repositories.CodingChallengeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CodingChallengeService {

    private final CodingChallengeRepository codingChallengeRepository;

    @Transactional
    public void deleteCodingChallenge(Long challengeId) {
        codingChallengeRepository.deleteById(challengeId);
    }

    @Transactional
    public void updateCodingChallenge(Long challengeId, CodingChallengeUpdateDTO codingChallengeUpdateDTO) {
        CodingChallenge codingChallenge = codingChallengeRepository.findById(challengeId)
                .orElseThrow(() -> new CodingChallengeNotFound("Coding challenge not found with id: " + challengeId));
        codingChallenge.setStarterCode(codingChallengeUpdateDTO.starterCode());
        codingChallenge.setReferenceSolution(codingChallengeUpdateDTO.referenceSolution());
        codingChallengeRepository.save(codingChallenge);
    }

    @Transactional
    public void createCodingChallenge(CodingChallenge codingChallenge) {
        codingChallengeRepository.save(codingChallenge);
    }
}
