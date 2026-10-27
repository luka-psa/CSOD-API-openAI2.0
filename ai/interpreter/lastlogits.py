# Only the last position's word scores: insert Slice(-1) before the vocabulary step (lm_head), in place.
import onnx, sys
from onnx import helper, TensorProto
src, dst = sys.argv[1], sys.argv[2]
m = onnx.load(f"{src}/model.onnx")
head = next(n for n in m.graph.node if "logits" in n.output)
h = head.input[0]
consts = [helper.make_tensor(n, TensorProto.INT64, [1], v) for n, v in [("last_starts", [-1]), ("last_ends", [2**62]), ("last_axes", [1])]]
m.graph.initializer.extend(consts)
m.graph.node.insert(list(m.graph.node).index(head), helper.make_node("Slice", [h, "last_starts", "last_ends", "last_axes"], ["last_hidden"], name="/last_position/Slice"))
head.input[0] = "last_hidden"
for o in m.graph.output:
    if o.name == "logits": o.type.tensor_type.shape.dim[1].dim_param = "one"
onnx.save(m, f"{dst}/model.onnx", save_as_external_data=True, all_tensors_to_one_file=True, location="model.onnx.data")
