# Read-only Play state for the release lanes (wear-release-pipeline.md §7.2 step 1). Every edit this
# opens is deleted, including on error, and nothing here decides anything: the lanes hand the state
# to the Python helpers under .github/scripts/.
module PlayState
  module_function

  # Yields a Supply client inside a fresh edit that is always deleted. Opening an edit invalidates
  # every other open edit of the app, so callers finish with this one before any upload opens its own.
  def read_only(json_key:, package_name:)
    require "supply"
    require "supply/options"
    config = FastlaneCore::Configuration.create(
      Supply::Options.available_options, { json_key: json_key, package_name: package_name }
    )
    client = Supply::Client.make_from_config(params: config)
    with_read_only_edit(client, package_name) { yield client }
  end

  def with_read_only_edit(client, package_name)
    client.begin_edit(package_name: package_name)
    begin
      yield
    ensure
      client.abort_current_edit
    end
  end

  # Every track with its releases' version codes, and the configured track's codes. Those are read
  # only when the track is listed: supply's #track_version_codes returns [] for a missing track.
  def tracks(client, configured)
    listed = client.tracks.map do |track|
      releases = Array(track.releases).map do |release|
        { "status" => release.status, "versionCodes" => Array(release.version_codes).map(&:to_i) }
      end
      { "id" => track.track, "releases" => releases }
    end
    codes = listed.any? { |track| track["id"] == configured } ? client.track_version_codes(configured).map(&:to_i) : nil
    { "tracks" => listed, "configuredTrack" => configured, "configuredTrackVersionCodes" => codes }
  end
end
