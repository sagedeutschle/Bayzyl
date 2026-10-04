import Foundation
@main struct Fixtures {
 static func main() throws {
  let board = CatanBoard.standard
  let topology: [String: Any] = [
   "centers": board.hexCenters.map { ["x": $0.x, "y": $0.y] },
   "vertices": board.vertices.map { ["x": $0.x, "y": $0.y] },
   "edges": board.edges.map { [$0.a, $0.b] },
   "hexVertices": board.hexVertexIndices, "vertexHexes": board.vertexHexIndices,
   "adjacency": board.vertexAdjacency, "vertexEdges": board.vertexEdgeIndices
  ]
  var vectors: [[String: Any]] = []
  for seed: UInt64 in [1, 42, UInt64.max] {
   let slide = SlidingPuzzle.shuffled(seed: seed)
   var cube = RubiksCube(); cube.scramble(seed: seed)
   func vec(_ v: CubeVec) -> [Int] { [v.x, v.y, v.z] }
   let cubes = cube.cubies.map { ["home": vec($0.home), "position": vec($0.position), "orientation": [vec($0.orientation.c0), vec($0.orientation.c1), vec($0.orientation.c2)]] as [String: Any] }
   var snake = SnakeGame(); var rng = SeededGenerator(seed: seed)
   snake.turn(.up); snake.step(rng: &rng)
   let catan = CatanGame.newGame(seed: seed)
   vectors.append(["seed": String(seed), "sliding": slide.tiles, "cube": cubes,
    "snake": snake.body.map { $0.row * 14 + $0.col },
    "catanTiles": catan.tiles.map { ["resource": $0.resource?.rawValue as Any? ?? NSNull(), "number": $0.number as Any? ?? NSNull()] },
    "catanDeck": catan.devDeck.map { $0.rawValue }, "catanRobber": catan.robberHex
   ])
  }
  let result: [String: Any] = ["topology": topology, "vectors": vectors]
  let data = try JSONSerialization.data(withJSONObject: result, options: [.sortedKeys])
  FileHandle.standardOutput.write(data)
 }
}
