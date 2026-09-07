pub const APPLICATION_VERSION: &str = match option_env!("OPENNOW_BUILD_VERSION") {
    Some(version) => version,
    None => env!("CARGO_PKG_VERSION"),
};

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn application_version_is_semver() {
        semver::Version::parse(APPLICATION_VERSION).expect("application version must be semver");
    }

    #[test]
    fn nightly_version_preserves_run_and_attempt() {
        let version = semver::Version::parse("1.0.0-nightly.123456.2").unwrap();
        assert_eq!((version.major, version.minor, version.patch), (1, 0, 0));
        assert_eq!(version.pre.as_str(), "nightly.123456.2");
        assert_eq!(version.to_string(), "1.0.0-nightly.123456.2");
    }
}
