package com.whfcd.vk_photo_downloader.services;

import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.photos.Photo;
import com.vk.api.sdk.objects.photos.PhotoAlbumFull;
import com.vk.api.sdk.objects.photos.PhotoSizes;
import com.vk.api.sdk.objects.photos.responses.GetResponse;
import com.whfcd.vk_photo_downloader.models.PhotoUrl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@RequiredArgsConstructor
public class MetaDataService {

    private static final int MAX_ITEMS_PER_REQUEST = 1000; // if more than 1000 in request - it will fail on VK side
    private final VkApiClient apiClient;
    private final UserActor actor;


    public List<PhotoAlbumFull> getAlbumsMetaDataFor(int userId) throws ApiException, ClientException {
        log.info("Getting info about all the albums belonging to the user with id {}...", userId);
        return apiClient.photos().getAlbums(actor)
                .ownerId(userId)
//                .needCovers(true)
                .needSystem(true)
                .execute()
                .getItems();
    }

    public Map<String, List<PhotoUrl>> getPhotoUrlsByAlbums(int userId,
                                                            List<PhotoAlbumFull> desiredAlbums) {
        log.info("Fetching links to photos...");
        return desiredAlbums.stream().collect(Collectors.toMap(PhotoAlbumFull::getTitle, photoAlbum -> {
            String albumLiteralId = mapAlbumNumericIdToLiteralId(photoAlbum.getId());

            // this is a workaround because of the obscurity of actual string tag for default -9000 album
            if (albumLiteralId.equals("me")) {
                return List.of();
            }

            List<String> photoUrls = new ArrayList<>();
            int numberOfRequests = (int) Math.ceil((float) photoAlbum.getSize() / MAX_ITEMS_PER_REQUEST);
            for (int i = 0; i < numberOfRequests; i++) {
                List<String> urls = requestUrlsFor(userId, albumLiteralId, i * MAX_ITEMS_PER_REQUEST);
                photoUrls.addAll(urls);
            }

            List<PhotoUrl> indexedPhotoUrls = IntStream.range(0, photoUrls.size())
                    .mapToObj(i -> new PhotoUrl(i + 1, photoUrls.get(i)))
                    .toList();

            log.info("   successfully fetched {} links for album named \"{}\"", photoUrls.size(), photoAlbum.getTitle());
            return indexedPhotoUrls;
        }));
    }

    private List<String> requestUrlsFor(int userId, String albumId, int offset) {
        try {
            GetResponse response = apiClient.photos().get(actor)
                    .ownerId(userId)
                    .offset(offset)
                    .count(MAX_ITEMS_PER_REQUEST)
                    .albumId(albumId)
                    .execute();

            return response.getItems().stream()
                    .map(this::getUrlOfTheLargestPhoto)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    private String mapAlbumNumericIdToLiteralId(int albumId) {
        return switch (albumId) {
            case -6 -> "profile";
            case -7 -> "wall";
            case -15 -> "saved";
            case -9000 -> "me";
            default -> String.valueOf(albumId);
        };
    }

    private Optional<String> getUrlOfTheLargestPhoto(Photo photo) {
        return photo.getSizes().stream()
                .max(Comparator.comparing(PhotoSizes::getHeight))
                .map(PhotoSizes::getUrl)
                .map(URI::toString);
    }

    public void save(Map<String, List<PhotoUrl>> photosByAlbumsMap) {
        log.info("Saving fetched links to file...");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("output.txt"))) {
            photosByAlbumsMap.forEach(
                    (albumName, photoUrls) -> saveAlbumMetaData(bw, albumName, photoUrls)
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveAlbumMetaData(final BufferedWriter bw, final String albumName, final List<PhotoUrl> photoUrls) {
        try {
            bw.write("%n%n%n%n%n%n%s%n".formatted(albumName));
            photoUrls.forEach(photo -> savePhotoUrl(bw, photo));
            int albumSize = photoUrls.size();
            log.info("   {} photo links from album \"{}\" saved to output.txt file", albumSize, albumName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void savePhotoUrl(final BufferedWriter bw, final PhotoUrl photo) {
        try {
            bw.write(photo.getUrl().concat("\n"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
