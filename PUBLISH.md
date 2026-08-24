# libfvad-jni - Publish

Build the native libs locally, see [README: Development](README.md#development).

Create a new release:

```shell
./mvnw release:prepare
./mvnw release:clean
```

Push the tag to GitHub:

```shell
git push --tags
```

GitHub Actions will publish the artifacts to Maven Central.
