# The one prompt format, shared by training, evaluation and (later) the app.
def args_text(args):
    return ",".join(f"{k}:<escape>{v}<escape>" for k, v in sorted(args.items()))

def answer(calls):
    if not calls: return "<start_function_call>call:nothing{}<end_function_call>"
    return "".join(f"<start_function_call>call:{n}{{{args_text(a)}}}<end_function_call>" for n, a in calls)

def prompt(date, lines):
    talk = "\n".join(f"- {l}" for l in lines)
    return f"<bos><start_of_turn>developer\nHearth weekoverleg, {date}. Zet om in acties.<end_of_turn>\n<start_of_turn>user\n{talk}<end_of_turn>\n<start_of_turn>model\n"

import re
CALL = re.compile(r"<start_function_call>call:(\w+)\{(.*?)\}<end_function_call>", re.S)
ARG = re.compile(r"(\w+):<escape>(.*?)<escape>", re.S)
def parse(text):
    return [(n, dict(ARG.findall(a))) for n, a in CALL.findall(text) if n != "nothing"]
