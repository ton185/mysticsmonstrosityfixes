package com.name.mysticmonstrosityfixes;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites a block-atlas fragment shader so Sampler0 is a 2D array and the page index
 * carried in the U coordinate selects the layer. Shaders that already sample an array are left as they are.
 * Entity and item shaders also draw mob skins with Sampler0, so those keep the 2D sampler and read a
 * second array only when U is at least 1.
 */
public final class BlockAtlasShaderPatch {
    public enum Status {
        PATCHED,
        UNTOUCHED,
        FAILED
    }

    public record Fragment(Status status, String source) {
    }

    private static final Set<String> PATCHED = ConcurrentHashMap.newKeySet();
    /** Entity and item shaders. Sampler0 stays sampler2D, so these names must not make {@link #usesArray} true. */
    private static final Set<String> SHARED = ConcurrentHashMap.newKeySet();
    private static final Pattern SAMPLER2D = Pattern.compile("(?<![A-Za-z0-9_])sampler2D\\s+Sampler0\\b");
    private static final Pattern SAMPLE = Pattern.compile("texture\\s*\\(\\s*Sampler0\\s*,\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*([,)])");
    private static final Pattern SAMPLE_LOD = Pattern.compile("textureLod\\s*\\(\\s*Sampler0\\s*,\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*,");
    private static final Pattern TEXTURE_SIZE = Pattern.compile("textureSize\\s*\\(\\s*Sampler0\\s*,");
    private static final Pattern LEFTOVER = Pattern.compile("texture(?:Lod)?\\s*\\(\\s*Sampler0\\s*,(?!\\s*mmf_blockAtlasUv\\b)");
    /** Solid ground litter samples the block atlas through AtlasSampler, not Sampler0. */
    private static final Pattern LITTER_ATLAS = Pattern.compile("texture\\s*\\(\\s*AtlasSampler\\s*,\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\)");
    private static final String LITTER_HELPER = """
            uniform sampler2DArray MmfPages;
            vec3 mmf_blockAtlasUv(vec2 uv) {
                return vec3(uv.x - floor(uv.x), uv.y, floor(uv.x));
            }
            vec4 mmf_litterAtlas(vec2 uv) {
                vec4 onPage = texture(MmfPages, mmf_blockAtlasUv(uv));
                vec4 onBase = texture(AtlasSampler, uv);
                return mix(onBase, onPage, step(1.0, uv.x));
            }
            """;
    /**
     * textureSize of a sampler2DArray is ivec3. The menu stage shader stores it in an ivec2, so the
     * page width and height are taken here and the call sites keep the ivec2 they were written for.
     */
    private static final String HELPER = """
            vec3 mmf_blockAtlasUv(vec2 uv) {
                return vec3(uv.x - floor(uv.x), uv.y, floor(uv.x));
            }
            ivec2 mmf_blockAtlasSize(int lod) {
                return textureSize(Sampler0, lod).xy;
            }
            """;
    /**
     * Declared just after Sampler0. The stage shader samples from {@code painted}, which is
     * above main, and GLSL 1.50 requires the function to exist before that call. Both lookups
     * stay outside the page test. This driver rejects texture() inside a branch that depends
     * on the interpolated U, and EmberLens then hides the shader log.
     */
    private static final String SHARED_HELPER = """
            uniform sampler2DArray MmfPages;
            vec3 mmf_blockAtlasUv(vec2 uv) {
                return vec3(uv.x - floor(uv.x), uv.y, floor(uv.x));
            }
            vec4 mmf_blockAtlasSample(vec2 uv) {
                vec4 onPage = texture(MmfPages, mmf_blockAtlasUv(uv));
                vec4 onBase = texture(Sampler0, uv);
                return mix(onBase, onPage, step(1.0, uv.x));
            }
            vec4 mmf_blockAtlasSampleLod(vec2 uv, float lod) {
                vec4 onPage = textureLod(MmfPages, mmf_blockAtlasUv(uv), lod);
                vec4 onBase = textureLod(Sampler0, uv, lod);
                return mix(onBase, onPage, step(1.0, uv.x));
            }
            """;

    private BlockAtlasShaderPatch() {
    }

    public static boolean usesArray(String name) {
        if (name == null) {
            return false;
        }
        return PATCHED.contains(name) || PATCHED.contains(simpleName(name));
    }

    public static boolean usesShared(String name) {
        if (name == null) {
            return false;
        }
        return SHARED.contains(name) || SHARED.contains(simpleName(name));
    }

    public static void remember(String name) {
        if (name == null) {
            return;
        }
        PATCHED.add(name);
        PATCHED.add(simpleName(name));
    }

    public static void forget(String name) {
        if (name == null) {
            return;
        }
        PATCHED.remove(name);
        PATCHED.remove(simpleName(name));
    }

    public static void rememberShared(String name) {
        if (name == null) {
            return;
        }
        SHARED.add(name);
        String simple = simpleName(name);
        SHARED.add(simple);
        // The block stage shader is a second program that reuses this fragment. Its instance name
        // is what bindSharedAtlas sees, and the fragment is compiled under the other name.
        if ("slate_stage_soft".equals(simple)) {
            SHARED.add("slate_stage_soft_block");
        }
    }

    public static void forgetShared(String name) {
        if (name == null) {
            return;
        }
        SHARED.remove(name);
        String simple = simpleName(name);
        SHARED.remove(simple);
        if ("slate_stage_soft".equals(simple)) {
            SHARED.remove("slate_stage_soft_block");
        }
    }

    /**
     * Entity, item, cutout, translucent, and GUI draws bind the block atlas or some other 2D texture
     * to the same Sampler0. A chest atlas, a fluid sheet, a gas texture, and a JEI icon are not the
     * block atlas. Glint scrolls U past 1 on its own texture, and shadow clamps U, so those stay 2D.
     */
    public static boolean isSharedAtlasShader(String name) {
        if (name == null) {
            return false;
        }
        String simple = simpleName(name);
        if (simple.contains("glint") || simple.contains("shadow") || simple.contains("armor")
                || simple.contains("eyes") || simple.contains("energy") || simple.contains("leash")
                || simple.contains("beacon") || simple.contains("lightning") || simple.contains("text")
                || simple.contains("particle")) {
            return false;
        }
        if (simple.contains("outline") && !simple.contains("no_outline")) {
            return false;
        }
        return simple.contains("slate_stage_soft")
                || simple.contains("rendertype_entity")
                || simple.contains("item_entity")
                || simple.contains("rendertype_translucent")
                || simple.contains("rendertype_cutout")
                || simple.contains("rendertype_tripwire")
                || simple.contains("position_tex");
    }

    /**
     * Vanilla block rendertypes, copies of those shaders added by mods, and this mod's terrain particle shader.
     * Entity, armor, text, and GUI shaders also use Sampler0, and those atlases stay 2D.
     */
    public static boolean isBlockAtlasShader(String name) {
        if (name == null) {
            return false;
        }
        String simple = simpleName(name);
        if (simple.equals("mmf_block_particle") || simple.equals("block_lit")) {
            return true;
        }
        if (simple.contains("entity") || simple.contains("armor") || simple.contains("glint")
                || simple.contains("text") || simple.contains("gui") || simple.contains("beacon")
                || simple.contains("leash") || simple.contains("lightning") || simple.contains("eyes")
                || simple.contains("outline") || simple.contains("energy") || simple.contains("portal")
                || simple.contains("particle") || simple.contains("position_tex")
                || simple.contains("rendertype_cutout") || simple.contains("rendertype_translucent")
                || simple.contains("rendertype_tripwire")) {
            return false;
        }
        return simple.contains("rendertype_solid")
                || simple.contains("block_atlas");
    }

    public static String simpleName(String name) {
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int colon = name.lastIndexOf(':');
        if (colon >= 0) {
            name = name.substring(colon + 1);
        }
        if (name.endsWith(".fsh") || name.endsWith(".vsh")) {
            name = name.substring(0, name.length() - 4);
        }
        return name;
    }

    public static Fragment patchFragment(String source) {
        if (source.contains("sampler2DArray Sampler0") || source.contains("sampler2DArray\tSampler0")) {
            return new Fragment(Status.PATCHED, source);
        }
        if (!SAMPLER2D.matcher(source).find()) {
            return new Fragment(Status.UNTOUCHED, source);
        }
        Matcher lods = SAMPLE_LOD.matcher(source);
        StringBuffer withLod = new StringBuffer();
        int replacements = 0;
        while (lods.find()) {
            replacements++;
            lods.appendReplacement(withLod, "textureLod(Sampler0, mmf_blockAtlasUv($1),");
        }
        lods.appendTail(withLod);
        Matcher samples = SAMPLE.matcher(withLod);
        StringBuffer rewritten = new StringBuffer();
        while (samples.find()) {
            replacements++;
            samples.appendReplacement(rewritten, "texture(Sampler0, mmf_blockAtlasUv($1)$2");
        }
        samples.appendTail(rewritten);
        Matcher sizes = TEXTURE_SIZE.matcher(rewritten);
        StringBuffer sized = new StringBuffer();
        while (sizes.find()) {
            replacements++;
            sizes.appendReplacement(sized, "mmf_blockAtlasSize(");
        }
        sizes.appendTail(sized);
        if (replacements == 0 || LEFTOVER.matcher(sized).find() || TEXTURE_SIZE.matcher(sized).find()) {
            return new Fragment(Status.FAILED, source);
        }
        String patched = SAMPLER2D.matcher(sized).replaceAll("sampler2DArray Sampler0");
        return new Fragment(Status.PATCHED, insertHelper(patched));
    }

    /**
     * Let There Be Atmosphere's solid litter shader stores a block sprite's U, including the page, and
     * samples it from {@code AtlasSampler}. That sampler is the page-0 view. U below 1 stays on it.
     * A later page samples {@code MmfPages}. Sampler0 is the litter model sheet and is left alone.
     * Both lookups stay outside the page test, for the same driver limit as {@link #patchSharedFragment}.
     */
    public static Fragment patchLitterAtlas(String source) {
        if (source.contains("vec4 mmf_litterAtlas")) {
            return new Fragment(Status.PATCHED, source);
        }
        if (!source.contains("sampler2D AtlasSampler") || !LITTER_ATLAS.matcher(source).find()) {
            return new Fragment(Status.UNTOUCHED, source);
        }
        Matcher samples = LITTER_ATLAS.matcher(source);
        StringBuffer rewritten = new StringBuffer();
        int replacements = 0;
        while (samples.find()) {
            replacements++;
            samples.appendReplacement(rewritten, "mmf_litterAtlas($1)");
        }
        samples.appendTail(rewritten);
        if (replacements == 0 || LITTER_ATLAS.matcher(rewritten).find()) {
            return new Fragment(Status.FAILED, source);
        }
        return new Fragment(Status.PATCHED, insertAfterSampler(rewritten.toString(), "sampler2D AtlasSampler", LITTER_HELPER));
    }

    /**
     * Keeps Sampler0 as sampler2D. U below 1, including a page-0 sprite held just under 1, samples that
     * texture. A later page, where U is at least 1, samples {@code MmfPages}.
     */
    public static Fragment patchSharedFragment(String source) {
        if (source.contains("vec4 mmf_blockAtlasSample")) {
            return new Fragment(Status.PATCHED, source);
        }
        if (!SAMPLER2D.matcher(source).find()) {
            return new Fragment(Status.UNTOUCHED, source);
        }
        Matcher lods = SAMPLE_LOD.matcher(source);
        StringBuffer withLod = new StringBuffer();
        int replacements = 0;
        while (lods.find()) {
            replacements++;
            lods.appendReplacement(withLod, "mmf_blockAtlasSampleLod($1,");
        }
        lods.appendTail(withLod);
        Matcher samples = SAMPLE.matcher(withLod);
        StringBuffer rewritten = new StringBuffer();
        while (samples.find()) {
            if (",".equals(samples.group(2))) {
                return new Fragment(Status.FAILED, source);
            }
            replacements++;
            samples.appendReplacement(rewritten, "mmf_blockAtlasSample($1)");
        }
        samples.appendTail(rewritten);
        if (replacements == 0 || LEFTOVER.matcher(rewritten).find()) {
            return new Fragment(Status.FAILED, source);
        }
        return new Fragment(Status.PATCHED, insertAfterSampler(rewritten.toString(), SHARED_HELPER));
    }

    /**
     * Puts {@code helper} on the line after {@code sampler2D Sampler0}, so functions above main can call it.
     * A shader that does not declare that sampler gets the helper in front of main.
     */
    private static String insertAfterSampler(String source, String helper) {
        return insertAfterSampler(source, "sampler2D Sampler0", helper);
    }

    private static String insertAfterSampler(String source, String declaration, String helper) {
        int sampler = source.indexOf(declaration);
        int lineEnd = sampler >= 0 ? source.indexOf('\n', sampler) : -1;
        if (lineEnd >= 0) {
            return source.substring(0, lineEnd + 1) + helper + source.substring(lineEnd + 1);
        }
        return insertBeforeMain(source, helper);
    }

    private static String insertHelper(String source) {
        if (source.contains("vec3 mmf_blockAtlasUv")) {
            return source;
        }
        int sampler = source.indexOf("sampler2DArray Sampler0");
        int lineEnd = sampler >= 0 ? source.indexOf('\n', sampler) : -1;
        if (lineEnd >= 0) {
            return source.substring(0, lineEnd + 1) + HELPER + source.substring(lineEnd + 1);
        }
        return source + HELPER;
    }

    private static String insertBeforeMain(String source, String helper) {
        int main = source.indexOf("void main");
        if (main >= 0) {
            return source.substring(0, main) + helper + source.substring(main);
        }
        return source + helper;
    }
}
