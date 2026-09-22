# ZapretDroid — GitHub Actions APK build

This copy contains a GitHub Actions workflow that builds the debug APK automatically.

## How to build

1. Create a new GitHub repository.
2. Upload all files from this folder to the repository.
3. Open **Actions** → **Build APK**.
4. If needed, choose **Run workflow**.
5. After the job finishes, open the workflow run.
6. Download the artifact **ZapretDroid-debug**.
7. Inside it is the generated APK.

The workflow uses JDK 21, Android SDK 34, and the project's existing Gradle wrapper.
