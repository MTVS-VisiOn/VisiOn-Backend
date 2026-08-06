package mtvs.onvision.vision.image.service;

import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.alert.domain.AlertType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageService {

    private final S3Service s3Service;

    public String saveImage(MultipartFile file, AlertType alertType) {
        String fileName = file.getOriginalFilename();
        String s3Key = generateS3Key(alertType, fileName);
        s3Service.upload(file, s3Key);
        return s3Key;
    }

    public String getPresignedUrl(String s3Key) {
        return s3Service.createShowPresignedUrl(s3Key);
    }

    public void deleteImage(String s3Key) {
        s3Service.delete(s3Key);
    }

    private static String generateS3Key(AlertType alertType, String fileName) {
        LocalDate now = LocalDate.now();
        String fileUuid = UUID.randomUUID().toString();
        return "alerts/%s/%d/%02d/%02d/%s/%s".formatted(
                alertType.name(),
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                fileUuid, fileName);
    }
}
