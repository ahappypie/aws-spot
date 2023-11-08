{
  description = "A Nix-flake-based Node.js development environment";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";

  outputs = { self, nixpkgs }:
    let
      overlays = [
        (final: prev: rec {
          nodejs = prev.nodejs_20;
        })
      ];

      supportedSystems = [ "x86_64-linux" "aarch64-linux" "x86_64-darwin" "aarch64-darwin" ];
      forEachSupportedSystem = f: nixpkgs.lib.genAttrs supportedSystems (system: f {
        pkgs = import nixpkgs { inherit overlays system; };
      });
    in
    {
      devShells = forEachSupportedSystem ({ pkgs }: {
        default = pkgs.mkShell {
          packages = with pkgs; [
              nodejs
          ];
        };
      });

      packages = forEachSupportedSystem ({ pkgs }: {
        aws-spot = pkgs.buildNpmPackage {
          name = "aws-spot";
          buildInputs = with pkgs; [
            nodejs
          ];

          src = ./.;

          npmDepsHash = "sha256-u5zJGyZDJHQhx702BXodxGfYmwwxY7dH4RSsKQVu5kc=";
        };
      });
    };
}
