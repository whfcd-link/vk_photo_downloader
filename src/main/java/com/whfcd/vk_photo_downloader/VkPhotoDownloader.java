package com.whfcd.vk_photo_downloader;

import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.photos.PhotoAlbumFull;
import com.whfcd.vk_photo_downloader.models.PhotoUrl;
import com.whfcd.vk_photo_downloader.services.MetaDataService;
import com.whfcd.vk_photo_downloader.services.PhotoService;
import lombok.extern.slf4j.Slf4j;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;

@Slf4j(topic = "logger")
public class VkPhotoDownloader {

    private static final VkApiClient apiClient;
    private static final UserActor actor;
    private static final PhotoService photoService;
    private static final MetaDataService metaDataService;


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

        actor = new UserActor(appId, token);
        apiClient = new VkApiClient(new HttpTransportClient());
        metaDataService = new MetaDataService(apiClient, actor);
        photoService = new PhotoService();
    }

    public static void main(String[] args) throws ClientException, ApiException {
        System.out.println("Step 1. Please, TYPE IN the id of VK-user OR VK-community OWNING the desired albums: \n"
                .concat("  (NOTE: if it is a community id - type it as a negative number, i. e. add minus before the actual id!) \n")
                .concat("  (NOTE: if you want to access albums of the authorized user - type in any character different from a number)")
        );
        Scanner scanner = new Scanner(System.in);
        int userId = getUserId(scanner);
        List<PhotoAlbumFull> albumsInfo = metaDataService.getAlbumsMetaDataFor(userId);
        System.out.printf("List of all the albums of owner with id %d: %n", userId);
        albumsInfo.forEach(album ->
                System.out.printf("   - %s, id: %d, size: %d%n", album.getTitle(), album.getId(), album.getSize())
        );

        System.out.println("\nStep 2. Please, TYPE IN the ids of desired albums separated with whitespaces (see list above): \n"
                .concat("  (For example:  -6 126038923 -7)\n")
                .concat("  (NOTE: after pressing ENT press CMD+D also to finish typing)\n")
                .concat("  (NOTE: if you want to download ALL THE ALBUMS at once, do not provide anything (just terminate typing with CMD+D combination) )")
        );
        scanner = new Scanner(System.in);
        Set<Integer> desiredIds = new HashSet<>();
        while (scanner.hasNextInt()) {
            desiredIds.add(scanner.nextInt());
        }

        log.info("Getting ready to download photos from the following albums belonging to owner with an id {}:", userId);
        List<PhotoAlbumFull> desiredAlbums = desiredIds.isEmpty() ? albumsInfo :
                albumsInfo.stream().filter(x -> desiredIds.contains(x.getId())).toList();
        desiredAlbums.forEach(album -> log.info("   {}, id: {}, size: {}", album.getTitle(), album.getId(), album.getSize()));

        Map<String, List<PhotoUrl>> urlsByAlbums = metaDataService.getPhotoUrlsByAlbums(userId, desiredAlbums);
        metaDataService.save(urlsByAlbums);
        photoService.getPhotos(urlsByAlbums);
    }

    private static int getUserId(Scanner scanner) throws ApiException, ClientException {
        return scanner.hasNextInt()
                ? scanner.nextInt()
                : apiClient.users().get(actor).execute().get(0).getId();
    }

}
