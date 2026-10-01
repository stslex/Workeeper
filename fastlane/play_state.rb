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

  # Every track with its releases' version codes, and the configured track's codes. For a listed
  # track they come from its listed entry; configuredTrackProbe: "listed", or for an unlisted track
  # what edits.tracks.get answers.
  def tracks(client, configured)
    listed = client.tracks.map do |track|
      releases = Array(track.releases).map do |release|
        { "status" => release.status, "versionCodes" => Array(release.version_codes).map(&:to_i) }
      end
      { "id" => track.track, "releases" => releases }
    end
    configured_entry = listed.find { |track| track["id"] == configured }
    # GUARD: never supply's #track_version_codes here. A listed track with no releases (a new Wear
    # track) carries `releases: nil`, and that method calls #flat_map on it.
    probe, codes = if configured_entry
                     ["listed", configured_entry["releases"].flat_map { |release| release["versionCodes"] }]
                   else
                     probe_unlisted(client, configured)
                   end
    { "tracks" => listed, "configuredTrack" => configured, "configuredTrackProbe" => probe,
      "configuredTrackVersionCodes" => codes }
  end

  # A track edits.tracks.list omits may still exist, and supply uploads into it (uploader.rb
  # #update_track builds the Track). edits.tracks.get, in the caller's read-only edit, answers:
  # a track ("found", with its releases' codes, decided like a listed one), or one of the two 404s
  # supply tells apart (client.rb #track_version_codes): "empty" for trackEmpty, "absent" for Track
  # not found. Any other error raises, so the decision never runs.
  def probe_unlisted(client, track)
    found = client.client.get_edit_track(client.current_package_name, client.current_edit.id, track)
    ["found", Array(found.releases).flat_map { |release| Array(release.version_codes) }.map(&:to_i)]
  rescue Google::Apis::ClientError => e
    raise unless e.status_code == 404
    empty = e.to_s.include?("trackEmpty")
    raise if empty == e.to_s.include?("Track not found")
    empty ? ["empty", []] : ["absent", nil]
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

  # Downloads a DRIFT artifact's images (listing_drift.py fetch.json, paths relative to `into`) for
  # `listing_drift.py adopt`.
  # Records the downloaded bytes' sha256 next to the API's, which is evidence for the spec's ASM-1.
  def download(fetch_list, into:, fetcher: method(:http_get))
    fetch_list.map do |item|
      body = fetcher.call(item.fetch("url"))
      extension = body.start_with?("\x89PNG".b) ? "png" : body.start_with?("\xFF\xD8".b) ? "jpg" : "bin"
      file = "#{item.fetch('path')}.#{extension}"
      FileUtils.mkdir_p(File.dirname(File.join(into, file)))
      File.binwrite(File.join(into, file), body)
      item.merge("file" => file, "downloadedSha256" => Digest::SHA256.hexdigest(body))
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
