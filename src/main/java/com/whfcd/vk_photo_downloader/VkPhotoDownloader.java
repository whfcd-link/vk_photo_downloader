package com.whfcd.vk_photo_downloader;

import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.photos.PhotoAlbumFull;
import com.vk.api.sdk.objects.photos.PhotoSizes;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

@Slf4j(topic = "com.whfcd.vk_photo_downloader")
public class VkPhotoDownloader {
    private static final int APP_ID;
    private static final String TOKEN;
    private static final int MAX_ITEMS_PER_REQUEST = 1000;

    private static final VkApiClient vk = new VkApiClient(new HttpTransportClient());
    private static final UserActor actor;

    static {
        int appId = 0;
        String token = "";

        try (Reader input = new FileReader("src/main/resources/application.properties")) {
            Properties prop = new Properties();
            prop.load(input);

            appId = Integer.parseInt(prop.getProperty("app.id"));
            token = prop.getProperty("app.token");
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        APP_ID = appId;
        TOKEN = token;
        actor = new UserActor(APP_ID, TOKEN);
    }

    public static void main(String[] args) throws ClientException, ApiException {
        log.info("Establishing connection...");
        int userId = vk.users().get(actor).execute().get(0).getId();
        log.info("Connected as a vk-user with an id {}", userId);

        List<PhotoAlbumFull> allAlbumsInfo = getAllAlbumsInfo(userId);
        List<Integer> desiredAlbumsIds = Arrays.asList(-7, -6, 288888977, 123586411);

        log.info("Getting ready to download photos from the following albums:");
        List<PhotoAlbumFull> desiredAlbums = allAlbumsInfo.stream()
                .filter(x -> desiredAlbumsIds.stream()
                        .anyMatch(y -> y.equals(x.getId())))
                .peek(x -> log.info("   {}, id: {}, size: {}", x.getTitle(), x.getId(), x.getSize()))
                .collect(Collectors.toList());

        downloadPhotos(getUrlsByAlbums(userId, desiredAlbums, true));
    }

    private static void downloadPhotos(Map<String, List<String>> photoUrlsByAlbumsMap) {
        int i = 0;
        byte[] buf = new byte[2048];
        URL url;

        log.info("Downloading photos...");
        for (Map.Entry<String, List<String>> entry : photoUrlsByAlbumsMap.entrySet()) {
            if (new File("photos/" + entry.getKey()).mkdirs()) {
                log.info("  folder \"{}\" created...", entry.getKey());
            }

            for (String photoUrl : entry.getValue()) {
                ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
                try {
                    url = new URL(photoUrl);

                    try (InputStream in = new BufferedInputStream(url.openStream())) {
                        int n;
                        while ((n = in.read(buf)) != -1) {
                            outputBytes.write(buf, 0, n);
                        }
                        outputBytes.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }

                try (FileOutputStream fos = new FileOutputStream("photos/" + entry.getKey() + "/" + i + ".jpg")) {
                    fos.write(outputBytes.toByteArray());
                    log.info("   photo {}.jpg downloaded successfully to folder \"{}\"...", i, entry.getKey());
                } catch (IOException e) {
                    log.error("   failed to download photo {}.jpg :(((", i);
                    e.printStackTrace();
                } finally {
                    i++;
                }
            }
        }

        log.info("Process completed: {} photos were downloaded", photoUrlsByAlbumsMap.values().stream()
                .map(List::size)
                .reduce(Integer::sum)
                .orElse(0));
    }

    private static Map<String, List<String>> getUrlsByAlbums(int userId, List<PhotoAlbumFull> desiredAlbums, boolean saveLinksToFile) throws ApiException, ClientException {
        Map<String, List<String>> photoUrlsByAlbumsMap = getUrlsByAlbums(userId, desiredAlbums);

        if (saveLinksToFile) {
            saveUrlsToPhotosToFile(photoUrlsByAlbumsMap);
        }

        return photoUrlsByAlbumsMap;
    }

    private static Map<String, List<String>> getUrlsByAlbums(int userId, List<PhotoAlbumFull> desiredAlbums) throws ApiException, ClientException {
        log.info("Fetching links to photos...");
        Map<String, List<String>> photoUrlsByAlbumsMap = new HashMap<>();
        int cycles;

        for (PhotoAlbumFull photoAlbum : desiredAlbums) {
            cycles = (int) Math.ceil((float) photoAlbum.getSize() / MAX_ITEMS_PER_REQUEST);
            List<String> photosList = new ArrayList<>(photoAlbum.getSize());

            String albumIdAsString;
            switch (photoAlbum.getId()) {
                case -6:
                    albumIdAsString = "profile";
                    break;
                case -7:
                    albumIdAsString = "wall";
                    break;
                case -15:
                    albumIdAsString = "saved";
                    break;
                case -9000:
                    albumIdAsString = "me"; // find out the proper tag!
                    break;
                default:
                    albumIdAsString = photoAlbum.getId().toString();
            }

            for (int i = 0; i < cycles; i++) {
                photosList.addAll(vk.photos().get(actor)
                        .ownerId(userId)
                        .offset(i * MAX_ITEMS_PER_REQUEST)
                        .count(MAX_ITEMS_PER_REQUEST)
                        .albumId(albumIdAsString)      // that causes a need to convert (see switch-case above) the numerical-id to its string form (for album of tagged photos string-id is unknown)
                        .execute()
                        .getItems().stream()
                        .map(photo -> photo.getSizes().stream()
                                .max(Comparator.comparing(PhotoSizes::getHeight))
                                .map(photoOfDesiredSize -> photoOfDesiredSize.getUrl().toString()).orElse(""))
                        .filter(x -> !x.equals(""))
                        .collect(Collectors.toList()));
            }

            photoUrlsByAlbumsMap.put(photoAlbum.getTitle(), photosList);
            log.info("   successfully fetched {} links for album named \"{}\"", photosList.size(), photoAlbum.getTitle());
        }
        return photoUrlsByAlbumsMap;
    }

    private static List<PhotoAlbumFull> getAllAlbumsInfo(int userId) throws ApiException, ClientException {
        log.info("Getting info about all the albums belonging to the user with id {}...", userId);

        List<PhotoAlbumFull> albumsInfo = vk.photos().getAlbums(actor)
                .ownerId(userId)
                .needCovers(true)
                .needSystem(true)
                .execute()
                .getItems();

        albumsInfo.forEach(x -> log.info("   {}, id: {}, size: {}", x.getTitle(), x.getId(), x.getSize()));
        return albumsInfo;
    }

    private static void saveUrlsToPhotosToFile(Map<String, List<String>> photoUrlsByAlbumsMap) {
        log.info("Saving fetched links to file...");

        try (BufferedWriter bw = new BufferedWriter(new FileWriter("output.txt"))) {
            for (Map.Entry<String, List<String>> entry : photoUrlsByAlbumsMap.entrySet()) {
                try {
                    bw.write(entry.getKey() + "\n");
                    entry.getValue().forEach(photo ->
                            {
                                try {
                                    bw.write(photo + "\n");
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                    );

                    log.info("   {} photos links from album \"{}\" saved to output.txt file", entry.getValue().size(), entry.getKey());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
