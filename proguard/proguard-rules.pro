-include firebase-analytics.pro
-include firebase-crashlytics.pro
-include gms.pro
-include kotlinx-serialization.pro

# AI-readable snapshot export DTOs + enums (drive-ai-export.md). Belt-and-suspenders over the
# generic kotlinx-serialization keeps in kotlinx-serialization.pro: keep the classes + all
# members so R8 cannot obfuscate the serialName or strip the generated serializers — the repo's
# known minified-@Serializable-enum failure mode. The JSON contract is also externally consumed
# (read by an LLM), so the field names must survive minification verbatim.
-keep class io.github.stslex.workeeper.core.data.database.export.model.** { *; }