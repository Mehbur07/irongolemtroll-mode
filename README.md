# Iron Golem Troll

Minecraft **26.2** için sunucu taraflı bir Fabric modu. Bir demir golemi vurduğunda golem eline demir blok alır, dibine kadar yürür ve senin hemen yanında altına demir blok koyarak 3 blok yukarı tırmanır. Bu bitene kadar kimseye vuramaz. Kuleni yükseltip tekrar vurursan golem zaten yanında olduğu için 3 blok daha tırmanır. Böylece sonsuza kadar yükselen bir demir kule oluşur.

Konulan demir bloklar kalıcıdır ve normal demir blok gibi kırılabilir.

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
| `climbBlocks` | `3` | Golemin her vuruşta altına koyduğu demir blok (tırmandığı blok) sayısı |
| `maxDistance` | `10.0` | Bundan uzaktan gelen vuruşlar (ör. ok) tırmanış başlatmaz |

## Komutlar (op, seviye 2)
| Komut | Ne yapar |
|---|---|
| `/igtroll on`, `/igtroll off`, `/igtroll toggle` | Trolü açar veya kapatır. `off` devam eden tırmanışları da durdurur |
| `/igtroll reload` | Ayar dosyasını yeniden okur |
| `/igtroll status` veya `/igtroll` | Durumu ve şu an tırmanan golem sayısını gösterir |

## Derleme
```
./gradlew build
```
Bu komut birim testlerini ve oyun içi testleri (GameTest) de çalıştırır. Jar `build/libs/` klasörüne çıkar.

Görsel kontrol için `./gradlew runClientGameTest` gerçek bir oyun penceresi açar. Bu testte oyuncu kulenin üstünden golemi sol tıkla vurur ve ekran görüntüleri `build/run/clientGameTest/screenshots/` klasörüne kaydedilir.

Windows'ta `install.bat` modu derler, MehburMC test instance'ına kurar ve eksikse Fabric API'yi indirir.

## Lisans
MIT
