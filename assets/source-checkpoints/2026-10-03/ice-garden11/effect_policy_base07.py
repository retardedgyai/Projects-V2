"""Unbound, single-owner visual-event policy. Never changes damage, CC or input.

The integration adapter must feed confirmed server hits and an opaque cast epoch.
This study proves lifecycle/budget handling, not that such an adapter is installed.
"""
from dataclasses import dataclass,field
@dataclass
class Contact:
 target:str
 ordinal:int
 at:float
@dataclass
class FieldCycle:
 epoch:object=None
 last_epoch:object=None
 cast_at:float=0
 active_at:float=0
 expires_at:float=0
 contacts:list=field(default_factory=list)
 sequences:dict=field(default_factory=dict)
 latest:float=float('-inf')
 CONTACT_SECONDS=.38
 END_SECONDS=.76
 MAX_CONTACTS=4
 MAX_TARGETS=128
 def begin(self,epoch,now):
  if epoch is None or epoch==self.last_epoch:return False
  self.clear();self.epoch=epoch;self.last_epoch=epoch;self.cast_at=now
  self.active_at=now+.30;self.expires_at=self.active_at+6.;self.latest=now
  return True
 def clear(self):
  self.epoch=None;self.contacts.clear();self.sequences.clear()
 def advance(self,now):
  if now<self.latest:return False
  self.latest=now
  self.contacts[:]=[c for c in self.contacts if 0<=now-c.at<self.CONTACT_SECONDS-1e-9 and now<self.expires_at]
  if self.epoch is not None and now>=self.expires_at+self.END_SECONDS:self.clear()
  return True
 def cancel_preparation(self,epoch,now):
  if epoch!=self.epoch or now>=self.active_at:return False
  self.clear();return True
 def confirmed_hit(self,epoch,target,ordinal,now,*,target_alive=True,supported=True,in_range=True):
  if not self.advance(now) or epoch!=self.epoch or epoch is None:return False
  if not self.active_at<=now<self.expires_at or not target_alive or not supported or not in_range:return False
  if not 1<=ordinal<=4:return False
  previous=self.sequences.get(target)
  if previous and (ordinal<=previous[0] or now-previous[1]<1.-1e-9):return False
  if previous is None and len(self.sequences)>=self.MAX_TARGETS:return False
  # Drop a crowded visual, preserving its identity. Damage has already been decided elsewhere.
  self.sequences[target]=(ordinal,now)
  if len(self.contacts)>=self.MAX_CONTACTS:return False
  self.contacts.append(Contact(target,ordinal,now));return True
 def target_invalid(self,target):
  self.contacts[:]=[c for c in self.contacts if c.target!=target]
 def owner_invalid(self,epoch):
  if epoch==self.epoch:self.clear();return True
  return False
 def displays(self,now):
  self.advance(now)
  if self.epoch is None:return 0
  if now<self.active_at:return 1
  if now<self.expires_at:return 8+len(self.contacts)*2+(1 if self.contacts else 0)
  return 15
