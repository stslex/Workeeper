# Read-only Play state for the release lanes (wear-release-pipeline.md §7.2 step 1, §8). Every edit
# this opens is deleted, including on error, and nothing here decides anything: the lanes hand the
# state to the Python helpers under .github/scripts/.
require "digest"
require "fileutils"

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

  # The remote values of exactly the items a listing_drift.py plan names (§8): per language its text
  # fields, and per image type its images in Play's order with their sha256 and URL.
  def listing(client, plan)
    languages = plan.fetch("languages").to_h do |language, wanted|
      entry = { "text" => {}, "images" => {} }
      unless wanted.fetch("text").empty?
        listing = client.listing_for_language(language)
        wanted["text"].each { |field| entry["text"][field] = listing.public_send(field) }
      end
      wanted.fetch("images").each do |type|
        entry["images"][type] = client.fetch_images(image_type: type, language: language).map do |image|
          { "id" => image.id, "sha256" => image.sha256, "url" => image.url }
        end
      end
      [language, entry]
    end
    { "languages" => languages }
  end

  # Downloads a DRIFT artifact's images (listing_drift.py fetch.json) so that reconciling is a copy.
  # Records the downloaded bytes' sha256 next to the API's, which is evidence for the spec's ASM-1.
  def download(fetch_list, fetcher: method(:http_get))
    fetch_list.map do |item|
      body = fetcher.call(item.fetch("url"))
      extension = body.start_with?("\x89PNG".b) ? "png" : body.start_with?("\xFF\xD8".b) ? "jpg" : "bin"
      path = "#{item.fetch('path')}.#{extension}"
      FileUtils.mkdir_p(File.dirname(path))
      File.binwrite(path, body)
      item.merge("file" => path, "downloadedSha256" => Digest::SHA256.hexdigest(body))
    end
  end

  def http_get(url, redirects = 3)
    require "net/http"
    response = Net::HTTP.get_response(URI(url))
    return response.body.b if response.is_a?(Net::HTTPSuccess)
    if response.is_a?(Net::HTTPRedirection) && redirects.positive?
      return http_get(response["location"], redirects - 1)
    end
    raise "GET #{url} failed: #{response.code}"
  end
end
