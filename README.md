# thergioni
## Introduction ##
Health checker / monitoring tool designed by a system adminitrator who doesn't like being woken up for nothing.

While Nagios is a good and valuable tool, relying on it for wakeup calls is entering a world of pain.
Instead we've found it to be an excellent "early warning" tool for casual day-to-day tidying up admin jobs.

Custom scripts, while more powerful, as they can be tailored to your own needs, have a tendancy to be inconsistent.
i.e. different servers / locations / schedules, notification and repeat policies etc.

Thergioni was built over 3 days of work leave with high fever and boredom.
It was designed to be the _one_ tool to centralize monitoring and alerting, and in our environment, has proven to be excellent.

## Design ##
Thergioni is designed to be dependancy aware, and able to distinguish blips from problems.

### Blips vs failures ###
Sometimes checks fail for no obvious reason. Sometimes network hiccups cause blips. Sometimes, app servers crash, but respawn 10 secs later and service is fine. While it's important to see that this happens, and try to improve whatever is improvable, being woken up because an app server restarted is not wanted.

Thergioni uses a simple system of multiple thresholds, that effectively deals with notification fatigue.

For instance, a normal check for a website has a size and a timeout threshold.
However since I have 3 load balancers, on 2 seperate isps, with Thergioni, I can check all 3 balancers, on both ips, and determine that at least 4 have to fail simultaneously for it to be a problem instead of a blip.
Further to that, to ensure it's a problem and not a passing matter, it needs to happen on 3 consecutive check runs.

So if I receive an sms about my site being down, that means that it's down. No shit.

### Dependancies ###
Instead of defining nodes, and checks per node, you set up "types", and dependancies on other types.
In turn, nodes _belong_ to those types, and get checked, when that type gets checked.

i.e. is MyService works, then it follow that the app server works also, so don't bother checking it.
On the contrary if MyService is down, then check the app servers, the mysql and my cached instances and send a notification that gives you a larger picture.

Similarly there are breaker types, to filter out false positives.
For instance, if my adsl goes down, or my checker machine has high load for some other reason, then my remote website check will most likely fail, even though the service will not actually have a problem.

## Making sense / Documentation ##
All available options are included in the sample config xml file, along with brief descriptions.

## Performance ##
Due to the fact that only the top level "types" are checked on every iteration, and dependancies are _only_ checked when errors are detected, it is not very server hungry. Having said that, my test environment is not very large, so any feedback on this would be welcome.



