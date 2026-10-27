#version 150
// mmf-block-atlas-page

uniform sampler2DArray Sampler0;
uniform sampler2D Sampler2;

uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 texCoord0;
in vec4 normal;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, vec3(texCoord0.x - floor(texCoord0.x), texCoord0.y, floor(texCoord0.x))) * vertexColor;
    fragColor = color * ColorModulator;
}
