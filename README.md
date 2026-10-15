# Iron Golem Troll

Minecraft **26.2** için sunucu taraflı bir Fabric modu. Bir demir golemi vurduğunda golem seni yukarı fırlatır. Sen vuruş yüksekliğinin 3 blok üstüne çıkınca golem de 3 blok fırlar. Havada tekrar vurursan döngü sonsuza kadar sürer.

## Gereksinimler
| Bileşen | Sürüm |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | 0.19.5+ |
| Fabric API | 0.161.0+26.2 |
| Java | 25 |

Mod sadece sunucuda çalışır. İstemcide yüklü olması gerekmez, yüklüyse de sorun çıkarmaz.

## Ayarlar (`config/iron_golem_troll.json`)
| Anahtar | Varsayılan | Açıklama |
|---|---|---|
| `enabled` | `true` | Trol açık mı |
| `riseHeight` | `3.0` | Oyuncunun çıkması gereken ve golemin yükseleceği blok |
| `triggerWindowTicks` | `40` | Vuruştan sonra yükselmenin sayıldığı süre (tick) |
| `maxDistance` | `10.0` | Bu mesafeden uzaklaşınca döngü biter |
| `cooldownResetSeconds` | `5` | Yerde bu kadar saniye vurmazsan döngü biter |
| `golemLaunchesPlayer` | `true` | Golem her vuruşta oyuncuyu da fırlatır |

## Komutlar (op, seviye 2)
| Komut | Ne yapar |
|---|---|
| `/igtroll on`, `/igtroll off`, `/igtroll toggle` | Trolü açar veya kapatır. `off` tüm aktif döngüleri de durdurur |
| `/igtroll reload` | Ayar dosyasını yeniden okur |
| `/igtroll status` veya `/igtroll` | Durumu ve aktif döngü sayısını gösterir |

## Derleme
```
./gradlew build
```
Bu komut 3 birim testini ve 12 oyun içi testi (GameTest) de çalıştırır. Jar `build/libs/` klasörüne çıkar.

Windows'ta `install.bat` modu derler, MehburMC test instance'ına kurar ve eksikse Fabric API'yi indirir.

## Lisans
MIT
