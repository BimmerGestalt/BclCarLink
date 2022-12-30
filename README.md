BCL Car Link
============

[![Release Download](https://img.shields.io/github/release/BimmerGestalt/BclCarLink.svg)](https://github.com/BimmerGestalt/BclCarLink/releases/latest)

IDrive remote car apps communicate over a USB or Bluetooth serial connection,
which is usually started by the MyBMW/Mini apps (and BMW/Mini Connected before that).
On some phones, this connection takes a long time to start up,
and sometimes doesn't even start at all.

BCL Car Link is an experiment to implement this connection with more debug logging,
and hopefully faster and more consistently.

BCL Car Link does still require that the car have Apps support enabled,
which is a subscription option for BMWs and a factory option for Minis,
and can not get around that requirement.
AAIdrive will still require that [MyBMW](https://play.google.com/store/apps/details?id=de.bmw.connected.mobile20.na) or [Mini](https://play.google.com/store/apps/details?id=de.mini.connected.mobile20.na) or [SupraConnect](https://play.google.com/store/apps/details?id=de.j29.connected.mobile20.na) be installed to provide the security module.
