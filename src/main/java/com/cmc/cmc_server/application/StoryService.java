package com.cmc.cmc_server.application;


import com.cmc.cmc_server.domain.Challenge;
import com.cmc.cmc_server.domain.Report;
import com.cmc.cmc_server.domain.Story;
import com.cmc.cmc_server.domain.User;
import com.cmc.cmc_server.dto.Image.ImageReq;
import com.cmc.cmc_server.dto.Image.ImageRes;
import com.cmc.cmc_server.dto.Story.GetStoryReq;
import com.cmc.cmc_server.dto.Story.GetStoryRes;
import com.cmc.cmc_server.dto.Story.ReportStoryReq;
import com.cmc.cmc_server.errors.CustomException;
import com.cmc.cmc_server.errors.ErrorCode;
import com.cmc.cmc_server.infra.ChallengeRepository;
import com.cmc.cmc_server.infra.ReportRepository;
import com.cmc.cmc_server.infra.StoryRepository;
import com.cmc.cmc_server.infra.UserRepository;
import com.cmc.cmc_server.s3.S3Uploader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class StoryService {

    private final StoryRepository storyRepository;
    private final S3Uploader s3Uploader;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ChallengeRepository challengeRepository;

    public ImageRes createPost(ImageReq imageReq) {
        List<String> postImages = uploadPostImages(imageReq);
        return ImageRes.builder().imageUrl(postImages).build();
    }

    /**
     * 이미지 파일 S3 저장 + PostImage 생성
     */
    private List<String> uploadPostImages(ImageReq imageReq) {
        Challenge challenge = challengeRepository.findById(imageReq.getChallengeId()).orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_FOUND));

        return imageReq.getImageFiles().stream()
                .map(image -> s3Uploader.upload(image, "post"))
                .map(url -> createStory(url, imageReq.getId(), challenge))
                .map(postImage -> postImage.getImageUrl())
                .collect(Collectors.toList());
    }

    /**
     * Story 생성 메서드
     */
    private Story createStory(String url, long id, Challenge challenge) {
        System.out.println("url = " + url);
        return storyRepository.save(Story.builder()
                .userId(id)
                .challenge(challenge)
                .imageUrl(url)
                .build());
    }

    // 스토리 신고
    public void reportStory(ReportStoryReq reportStoryReq) {
        long storyId = reportStoryReq.getStoryId();
        Story story = storyRepository.findById(storyId).orElseThrow(() -> new CustomException(ErrorCode.STORY_NOT_FOUND));

        story.setReport(story.getReport()+1);
        storyRepository.save(story);

        this.checkStory(story);
    }

    // 스토리 신고 과다 체크
    private void checkStory(Story story) {
        if(story.getReport() >= 3) {
            storyRepository.delete(story);
        }
    }

    // 스토리 조회
    public GetStoryRes getStory(GetStoryReq getStoryReq) {
        User user = userRepository.findById(getStoryReq.getUserId()).orElseThrow(() -> new CustomException(ErrorCode.STORY_NOT_FOUND));
        List<Story> story = storyRepository.findByUserIdAndChallenge_Id(getStoryReq.getUserId(), getStoryReq.getChallengeId());

        List<String> urls = new ArrayList<>();
        for (Story s : story) {
            urls.add(s.getImageUrl());
        }

        return new GetStoryRes(user.getNickname(), urls);
    }
}
