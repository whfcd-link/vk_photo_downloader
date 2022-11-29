# VK Photo Downloader
A java-based appplication dedicated to download all the photos from specified albums from VK social network.

To use that app follow that simple steps:
1. Clone the repository to your local machine
2. In the project folder create a file *src/main/resources/application.properties*
3. Type the following properties to the created file *application.properties*:

        app.id=51489953
        app.token=

4. As you can see the token is missing. To make it possible for the app to view and download desired albums one must get that token from VK (on current app version this must be done manually):
   1. Log in on *vk.com* to the profile that has permission to view the desired albums (ones you want to download)
   2. Follow [that link](https://oauth.vk.com/authorize?client_id=51489953&display=page&redirect_uri=https://oauth.vk.com/blank.html&scope=photos&response_type=token&v=5.131) in the browser (in which the log in was performed), submit the required permissions (acceess to personal info and photos)
   3.  After submitting you will be redirected to the page https://oauth.vk.com/blank.html. Find the property *access_token* in the URL, copy its value and past it as an *app.token* value to *application.properties* file (see step 3).

5. Save chages of the *application.properties*
6. Run the application... 


***The current version downloads all the albums from profile from step 4.1. Options to choose will be provided soon***

*Under the development*
