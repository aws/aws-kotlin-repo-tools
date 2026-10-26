## AWS Kotlin Repository Tools

This repository contains shared tooling for AWS Kotlin repositories:

* `aws/aws-sdk-kotlin`
* `smithy-lang/smithy-kotlin`
* `aws/aws-crt-kotlin`

It contains shared custom Gradle plugins and build logic used to build and release those repositories.

## Release

The plugins in this repository are not generally applicable and as such are not published to the 
[Gradle plugin portal](https://plugins.gradle.org/). Instead, they are published to an S3 bucket
hosted by the SDK team and fronted by a CloudFront distribution to create a "private" maven repository that
hosts the released versions of these plugins/artifacts.

The release logic is entirely contained in `scripts/release.sh`. By default, it will use the latest git tag for the
version that is being released.

To cut a new release:

1. Go to this repo's GitHub actions.
2. Locate the release workflow.
3. Specify whether the release will be of a kn variant (used for Kotlin Native development).
4. If you're doing a minor or major version bump, specify the version override (including "-kn" if a kn variant).
5. Run the workflow.

The workflow will create a tag, push it to this repo and then start a 
CodeBuild release job hosted in the shared tools account (e.g. `publish-aws-kotlin-repo-tools`).

<details>
<summary>Old manual release instructions</summary>

1. Create a new tag, e.g. `git tag x.y.z`.
2. Push the tag up `git push origin x.y.z`.
3. Go to the CodeBuild release job hosted in the shared tools account (e.g. `publish-aws-kotlin-repo-tools`).
4. Start a build with overrides.
5. Under `Source` connect your GitHub account (Under `Source` -> `Connection Status` you should see "You are connected to GitHub").
6. Specify the tag you created under `Source Version`.
7. Start the build.

</details>

## Development

### Local development
To use a local version of the plugin in downstream projects (such as `smithy-kotlin` or `aws-sdk-kotlin`):
1. Run `./gradlew -Prelease.version=<YOUR_SNAPSHOT_VERSION> publishToMavenLocal`
2. Consume the snapshot plugin version in the projects 

### Project Structure

* `:build-plugins:build-support`   - common build support (publishing, linting, utils, etc)
* `:build-plugins:kmp-conventions` - Plugin that applies common conventions for KMP projects (source sets, targets, etc)
* `:build-plugins:smithy-build`    - Opinionated plugin that wraps the `smithy-base` plugin with a DSL for generating projections dynamically
* `:ktlint-rules`                  - Custom ktlint rules (consumed by `build-support` at runtime)

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This project is licensed under the Apache-2.0 License.

