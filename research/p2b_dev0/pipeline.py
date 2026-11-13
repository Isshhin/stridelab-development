"""Deterministic synthetic-only P2-B DEV-0 reference pipeline."""
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from enum import Enum
from hashlib import sha256
from math import hypot, sqrt
import json
import xml.etree.ElementTree as ET

FOUR_TU_DOI = "10.4121/efa64223-ba51-48fa-91be-53f0e48460b4.v1"
FOUR_TU_ADMISSION = "RESEARCH_ONLY_ISOLATED"
ROUTE_TOLERANCES = {"STRICT": {"endpoint": .0008, "path_fraction": .03, "shape": .0008}, "MODERATE": {"endpoint": .002, "path_fraction": .20, "shape": .002}, "LOOSE": {"endpoint": .005, "path_fraction": .30, "shape": .005}}

class AthleteState(Enum):
    ESTIMABLE="ESTIMABLE"; INSUFFICIENT_REPEATS="INSUFFICIENT_REPEATS"; INSUFFICIENT_ROUTE_OVERLAP="INSUFFICIENT_ROUTE_OVERLAP"; INSUFFICIENT_HR_MATCHES="INSUFFICIENT_HR_MATCHES"; INSUFFICIENT_TRACE_QUALITY="INSUFFICIENT_TRACE_QUALITY"; UNSUPPORTED_MEASUREMENT="UNSUPPORTED_MEASUREMENT"

@dataclass(frozen=True)
class Activity:
    athlete_id: str; activity_id: str; date: str; samples: tuple; source_dataset: str="synthetic-dev0"; route_direction: str="OUT"; temperature_c: float|None=None; relative_humidity_pct: float|None=None
@dataclass(frozen=True)
class AnalysisConfig:
    window_s: int=1200; start_s: int=720; hr_caliper_bpm: int=3; route_tolerance: str="STRICT"

def _key(a): return (a.source_dataset,a.athlete_id,a.activity_id)
def _label(k):
    return "|".join(part.replace("%", "%25").replace("|", "%7C") for part in k)
def _window(a,c): return tuple(dict(s,t=s["t"]-c.start_s) for s in a.samples if c.start_s<=s["t"]<=c.start_s+c.window_s)
def _qc(samples,duration):
    if any(b["t"]<=a["t"] for a,b in zip(samples,samples[1:])):return False,("NON_MONOTONIC_TIMESTAMP",)
    if not samples or samples[0]["t"]>0 or samples[-1]["t"]<duration:return False,("WINDOW_COVERAGE",)
    if max((b["t"]-a["t"] for a,b in zip(samples,samples[1:])),default=0)>5:return False,("UNRECOVERABLE_GAP",)
    hr=[s["hr"] for s in samples if s.get("hr") is not None]; speed=[s["speed"] for s in samples if s.get("speed") is not None]
    if any(not 30<=x<=240 for x in hr):return False,("HR_PHYSIOLOGICAL_BOUNDS",)
    if any(x<0 or x>12 for x in speed):return False,("UNPHYSICAL_SPEED",)
    if any(s.get("pause",False) for s in samples):return False,("LONG_PAUSE",)
    return True,()
def _weighted(samples,key):
    total=value=0.0
    for i,s in enumerate(samples):
        carry=min(5,samples[i+1]["t"]-s["t"]) if i+1<len(samples) else 0
        if s.get(key) is not None:value+=s[key]*carry;total+=carry
    return value/total if total else None
def _features(gps):
    n=9; step=max(1,(len(gps)-1)/(n-1)); shape=tuple((gps[round(i*step)][0]-gps[0][0],gps[round(i*step)][1]-gps[0][1]) for i in range(n)); length=sum(hypot(b[0]-a[0],b[1]-a[1]) for a,b in zip(gps,gps[1:]))
    return {"start":gps[0],"end":gps[-1],"path_length":round(length,9),"shape":shape}
def _segment(a,c):
    s=_window(a,c); k=_label(_key(a)); fixed=bool(s and s[0]["t"]==0 and s[-1]["t"]==c.window_s)
    supported=bool(s and sum(x.get("hr") is not None for x in s)/len(s)>=.95 and sum(x.get("speed") is not None for x in s)/len(s)>=.95 and all(x.get("lat") is not None and x.get("lon") is not None for x in s))
    ok,reasons=_qc(s,c.window_s)
    if not ok:return {"identity":k,"source_dataset":a.source_dataset,"athlete_id":a.athlete_id,"activity_id":a.activity_id,"fixed_window_available":fixed,"supported_measurement":supported,"qc_status":"REJECT","exclusion_reasons":reasons}
    gps=tuple((round(x["lat"],5),round(x["lon"],5)) for x in s if x.get("lat") is not None and x.get("lon") is not None)
    return {"identity":k,"source_dataset":a.source_dataset,"athlete_id":a.athlete_id,"activity_id":a.activity_id,"start_time":a.date,"segment_start":c.start_s,"segment_end":c.start_s+c.window_s,"duration_s":c.window_s,"fixed_window_available":fixed,"supported_measurement":supported,"mean_hr_bpm":round(_weighted(s,"hr"),6),"mean_speed_mps":round(_weighted(s,"speed"),6),"valid_hr_fraction":round(sum(x.get("hr") is not None for x in s)/len(s),6),"valid_speed_fraction":round(sum(x.get("speed") is not None for x in s)/len(s),6),"max_sample_gap_s":max((b["t"]-d["t"] for d,b in zip(s,s[1:])),default=0),"route_features":_features(gps) if supported else None,"route_direction":a.route_direction,"temperature_c":a.temperature_c,"relative_humidity_pct":a.relative_humidity_pct,"speed_method":"TIME_WEIGHTED_DEVICE_SPEED","qc_status":"PASS","exclusion_reasons":()}
def _route_compatible(a,b,tolerance):
    if a["route_direction"]!=b["route_direction"]:return False
    t=ROUTE_TOLERANCES[tolerance]; fa,fb=a["route_features"],b["route_features"]
    if hypot(fa["start"][0]-fb["start"][0],fa["start"][1]-fb["start"][1])>t["endpoint"]:return False
    if hypot(fa["end"][0]-fb["end"][0],fa["end"][1]-fb["end"][1])>t["endpoint"]:return False
    if abs(fa["path_length"]-fb["path_length"])/max(fa["path_length"],fb["path_length"],1e-9)>t["path_fraction"]:return False
    return max(hypot(x[0]-y[0],x[1]-y[1]) for x,y in zip(fa["shape"],fb["shape"]))<=t["shape"]
def _groups(rows,tolerance):
    groups=[]
    for row in sorted(rows,key=lambda x:x["identity"]):
        for group in groups:
            if _route_compatible(row,group[0],tolerance):group.append(row);break
        else:groups.append([row])
    return {sha256(repr((tolerance,g[0]["route_features"],g[0]["route_direction"])).encode()).hexdigest()[:12]:g for g in groups}
def _pairs(rows,caliper):
    rows=sorted(rows,key=lambda x:(x["start_time"],x["identity"]));out=[]
    for left,right in zip(rows[::2],rows[1::2]):
        spacing=datetime.fromisoformat(right["start_time"])-datetime.fromisoformat(left["start_time"])
        if timedelta(days=1)<=spacing<=timedelta(days=7):out.append({"earlier":left["identity"],"later":right["identity"],"hr_diff":round(abs(right["mean_hr_bpm"]-left["mean_hr_bpm"]),6),"d":round(right["mean_speed_mps"]-left["mean_speed_mps"],6),"hr_compatible":abs(right["mean_hr_bpm"]-left["mean_hr_bpm"])<=caliper})
    return out
def _canonical(activities):
    unique={};duplicates=0
    for a in activities:
        k=_key(a)
        if k in unique:
            if unique[k]!=a:raise ValueError(f"conflicting content for scoped identity {_label(k)}")
            duplicates+=1
        else:unique[k]=a
    return unique,duplicates
def _analysis(unique,c):
    segs={_label(k):_segment(a,c) for k,a in sorted(unique.items())};valid=[x for x in segs.values() if x["qc_status"]=="PASS" and x["supported_measurement"]];candidates=[];pairs=[];route_count=0
    for athlete in sorted({a.athlete_id for a in unique.values()}):
        groups=_groups([x for x in valid if x["athlete_id"]==athlete],c.route_tolerance)
        for route,group in sorted(groups.items()):
            if len(group)>=2:
                route_count+=len(group)
                for pair in _pairs(group,c.hr_caliper_bpm):
                    item={"athlete_id":athlete,"route_group_id":route,**pair}; candidates.append(item)
                    if pair["hr_compatible"]:pairs.append(item)
    return segs,sorted(candidates,key=lambda x:(x["athlete_id"],x["earlier"])),sorted(pairs,key=lambda x:(x["athlete_id"],x["earlier"])),route_count
def _stats(ps):
    ds=[p["d"] for p in ps];bias=sum(ds)/len(ds) if ds else None;sd=sqrt(sum((x-bias)**2 for x in ds)/(len(ds)-1)) if len(ds)>1 else None
    return {"signed_bias":round(bias,6) if bias is not None else None,"sd_differences":round(sd,6) if sd is not None else None,"TE":round(sd/sqrt(2),6) if sd is not None else None,"agreement_limits":[round(bias-1.96*sd,6),round(bias+1.96*sd,6)] if sd is not None else None}
def _one(unique,c):
    segs,candidates,pairs,route_count=_analysis(unique,c);fixed=[x for x in segs.values() if x["fixed_window_available"]];rows=[x for x in segs.values() if x["qc_status"]=="PASS"];supported=[x for x in rows if x["supported_measurement"]]
    return {"config":{"window_s":c.window_s,"start_s":c.start_s,"hr_caliper_bpm":c.hr_caliper_bpm,"route_tolerance":c.route_tolerance},"segments":segs,"chronological_candidate_pairs":candidates,"pairs":pairs,"fixed_window_count":len(fixed),"qc_pass_count":len(rows),"supported_measurement_count":len(supported),"route_compatible_count":route_count,"chronological_candidate_pair_count":len(candidates),"hr_compatible_pair_count":len(pairs),"final_pair_count":len(pairs)}
def run(activities):
    activities=tuple(activities);unique,duplicates=_canonical(activities);analyses={"primary":_one(unique,AnalysisConfig()),"sensitivities":{"window_900s":_one(unique,AnalysisConfig(900)),"hr_2bpm":_one(unique,AnalysisConfig(hr_caliper_bpm=2)),"hr_5bpm":_one(unique,AnalysisConfig(hr_caliper_bpm=5)),"route_MODERATE":_one(unique,AnalysisConfig(route_tolerance="MODERATE")),"route_LOOSE":_one(unique,AnalysisConfig(route_tolerance="LOOSE"))}}
    p=analyses["primary"];states={}
    for athlete in sorted({a.athlete_id for a in unique.values()}):
        segs=[x for x in p["segments"].values() if x["athlete_id"]==athlete];rows=[x for x in segs if x["qc_status"]=="PASS"];supported=[x for x in rows if x["supported_measurement"]];candidates=[x for x in p["chronological_candidate_pairs"] if x["athlete_id"]==athlete];ps=[x for x in p["pairs"] if x["athlete_id"]==athlete];rejected=[x for x in segs if x["qc_status"]=="REJECT"];groups=_groups(supported,"STRICT");route_rows=sum(len(g) for g in groups.values() if len(g)>=2);stats=_stats(ps)
        state=AthleteState.ESTIMABLE if len(ps)>=3 else AthleteState.INSUFFICIENT_TRACE_QUALITY if not rows else AthleteState.UNSUPPORTED_MEASUREMENT if not supported else AthleteState.INSUFFICIENT_REPEATS if len(supported)<2 else AthleteState.INSUFFICIENT_ROUTE_OVERLAP if route_rows==0 else AthleteState.INSUFFICIENT_HR_MATCHES if candidates and not ps else AthleteState.INSUFFICIENT_REPEATS
        states[athlete]={"state":state.value,"raw_input_count":sum(a.athlete_id==athlete for a in activities),"canonical_candidate_count":len(segs),"duplicate_count":sum(1 for a in activities if a.athlete_id==athlete)-len(segs),"final_pair_count":len(ps),"fixed_window_count":sum(x.get("fixed_window_available",False) for x in segs),"qc_pass_count":len(rows),"supported_measurement_count":len(supported),"route_compatible_count":route_rows,"chronological_candidate_pair_count":len(candidates),"hr_compatible_pair_count":len(ps),**stats}
    return {"schema":"p2b-dev0-v2","raw_input_count":len(activities),"canonical_candidate_count":len(unique),"duplicate_count":duplicates,"fixed_window_count":p["fixed_window_count"],"qc_pass_count":p["qc_pass_count"],"supported_measurement_count":p["supported_measurement_count"],"route_compatible_count":p["route_compatible_count"],"chronological_candidate_pair_count":p["chronological_candidate_pair_count"],"hr_compatible_pair_count":p["hr_compatible_pair_count"],"final_pair_count":p["final_pair_count"],"segments":p["segments"],"pairs":p["pairs"],"athletes":states,"analysis":analyses,"route_tolerances":ROUTE_TOLERANCES}
def report(activities):return json.dumps(run(activities),sort_keys=True,separators=(",",":"),default=str)
def speed_blinded(activities):return tuple(Activity(a.athlete_id,a.activity_id,a.date,tuple(dict(s,speed=9.0-s.get("speed",0)) for s in a.samples),a.source_dataset,a.route_direction,a.temperature_c,a.relative_humidity_pct) for a in activities)
def parse_tcx(data):
    root=ET.fromstring(data);rows=[]
    for activity in root.findall(".//{*}Activity"):
        points=[]
        for p in activity.findall(".//{*}Trackpoint"):
            t=p.findtext("{*}Time");hr=p.findtext("{*}HeartRateBpm/{*}Value");lat=p.findtext("{*}Position/{*}LatitudeDegrees");lon=p.findtext("{*}Position/{*}LongitudeDegrees")
            if t:points.append({"time":t,"hr":int(hr) if hr else None,"lat":float(lat) if lat else None,"lon":float(lon) if lon else None})
        rows.append(points)
    return rows
def build_synthetic_fixture():
    base=datetime(2026,1,1,tzinfo=timezone.utc);out=[]
    def add(athlete,n,hr=150,route="A",direction="OUT",bad=None,speed=3.0):
        samples=tuple({"t":t,"hr":hr+(t//600)*4,"speed":speed+n*.01,"lat":({"A":0.0,"B":1.0,"C":2.0}[route])+t/120000,"lon":0.0 if route=="A" else 1.0} for t in range(720,1921,5))
        if bad=="hr":samples=tuple(dict(s,hr=None) if 820<=s["t"]<920 else s for s in samples)
        if bad=="gap":samples=tuple(s for s in samples if s["t"]<820 or s["t"]>850)
        if bad=="pause":samples=tuple(dict(s,pause=820<=s["t"]<850) for s in samples)
        if bad=="spike":samples=tuple(dict(s,speed=20 if s["t"]==1220 else s["speed"]) for s in samples)
        if bad=="gps":samples=tuple(dict(s,lat=None,lon=None) if 1120<=s["t"]<1220 else s for s in samples)
        out.append(Activity(athlete,f"{athlete}-{n:02d}",(base+timedelta(days=n)).isoformat(),samples,route_direction=direction))
    for n in range(6):add("healthy",n)
    for n in range(3):add("hr_mismatch",n,hr=150+n*15)
    for n,route in enumerate(("A","B","C")):add("route_mismatch",n,route=route)
    add("trace_bad",0,bad="hr");add("trace_bad",1,bad="gap");add("trace_bad",2,bad="spike");add("unsupported",0,bad="gps");out.append(out[-1]);return tuple(out)
