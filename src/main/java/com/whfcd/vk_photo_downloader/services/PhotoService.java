package com.whfcd.vk_photo_downloader.services;

import com.whfcd.vk_photo_downloader.models.PhotoUrl;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Slf4j
public class PhotoService {


    public void getPhotos(Map<String, List<PhotoUrl>> photoUrlsByAlbums) {
        log.info("Downloading photos...");
        photoUrlsByAlbums.forEach((albumName, photoUrlsList) -> {
            if (new File("photos/" + albumName).mkdirs()) {
                log.info("  folder \"{}\" created...", albumName);
            }

            if (photoUrlsList.size() > 15) {
                photoUrlsList.parallelStream().forEach(photoUrl -> getSinglePhoto(albumName, photoUrl));
            } else {
                photoUrlsList.forEach(photoUrl -> getSinglePhoto(albumName, photoUrl));
            }
        });

        log.info("Process completed!");
    }

    private void getSinglePhoto(String destFolder, PhotoUrl photoUrl) {
        String url = photoUrl.getUrl();
        try {
            BufferedImage bufferedImage = ImageIO.read(new URL(url));
            String photoName = buildPhotoName(destFolder, photoUrl);
            Path destinationPath = Paths.get("photos", destFolder, photoName);
            ImageIO.write(bufferedImage, "jpg", destinationPath.toFile());
            log.info("   successfully downloaded {}...", destinationPath);
        } catch (IOException e) {
            e.printStackTrace();
            log.error("   failed to download photo with index {} ({}), ", photoUrl.getId(), url);
        }
    }

    private String buildPhotoName(String destFolder, PhotoUrl photoUrl) {
        return String.format(
                "%s_%d.jpg",
                destFolder.replace(" ", "_").toLowerCase(),
                photoUrl.getId()
        );
    }

}
