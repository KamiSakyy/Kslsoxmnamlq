import datetime
import hashlib
import os
import sys

SEED = "YORU-release-key-v1"
BITS = 2048
ALIAS = "yoru"
STORE_PASSWORD = "yoru-release-store-v1"
KEY_PASSWORD = "yoru-release-key-v1"

SMALL_PRIMES = [3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97,
                101, 103, 107, 109, 113, 127, 131, 137, 139, 149, 151, 157, 163, 167, 173, 179, 181, 191, 193, 197, 199,
                211, 223, 227, 229, 233, 239, 241, 251, 257, 263, 269, 271, 277, 281, 283, 293, 307, 311, 313, 317, 331,
                337, 347, 349, 353, 359, 367, 373, 379, 383, 389, 397, 401, 409, 419, 421, 431, 433, 439, 443, 449, 457,
                461, 463, 467, 479, 487, 491, 499, 503, 509, 521, 523, 541, 547, 557, 563, 569, 571, 577, 587, 593, 599,
                601, 607, 613, 617, 619, 631, 641, 643, 647, 653, 659, 661, 673, 677, 683, 691, 701, 709, 719, 727, 733,
                739, 743, 751, 757, 761, 769, 773, 787, 797, 809, 811, 821, 823, 827, 829, 839, 853, 857, 859, 863, 877,
                881, 883, 887, 907, 911, 919, 929, 937, 941, 947, 953, 967, 971, 977, 983, 991, 997]
MR_BASES = [2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71,
            73, 79, 83, 89, 97, 101, 103, 107, 109, 113, 127, 131, 137, 139, 149, 151, 157, 163, 167, 173]


def stream(label, size):
    out = b""
    counter = 0
    while len(out) < size:
        out += hashlib.sha256(SEED.encode() + b"|" + label.encode() + counter.to_bytes(4, "big")).digest()
        counter += 1
    return out[:size]


def probable_prime(label, bits):
    data = stream(label, bits // 8 + 32)
    candidate = int.from_bytes(data[:bits // 8], "big")
    candidate |= (1 << (bits - 1)) | 1
    step = 0
    while True:
        value = candidate + step
        if value % 2 == 0:
            step += 2 if step else 0
            continue
        composite = False
        for prime in SMALL_PRIMES:
            if value % prime == 0 and value != prime:
                composite = True
                break
        if not composite and miller_rabin(value):
            return value
        step += 2


def miller_rabin(value):
    witness = value - 1
    shifts = 0
    while witness % 2 == 0:
        witness //= 2
        shifts += 1
    for base in MR_BASES:
        if base % value == 0:
            continue
        check = pow(base, witness, value)
        if check == 1 or check == value - 1:
            continue
        for _ in range(shifts - 1):
            check = (check * check) % value
            if check == value - 1:
                break
        else:
            return False
    return True


def build_numbers():
    exp = 65537
    attempt = 0
    while True:
        prime_a = probable_prime("p|" + str(attempt), BITS // 2)
        prime_b = probable_prime("q|" + str(attempt), BITS // 2)
        if prime_a == prime_b:
            attempt += 1
            continue
        phi = (prime_a - 1) * (prime_b - 1)
        if phi % exp == 0:
            attempt += 1
            continue
        try:
            private = pow(exp, -1, phi)
        except ValueError:
            attempt += 1
            continue
        return prime_a, prime_b, exp, private


def main():
    try:
        from cryptography import x509
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.serialization import pkcs12
        from cryptography.hazmat.primitives.asymmetric import rsa
        from cryptography.x509.oid import NameOID
    except ImportError:
        print("cryptography is required: pip install cryptography")
        return 1
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    target = os.path.join(root, "yoru-android", "owner-signing")
    os.makedirs(target, exist_ok=True)
    prime_a, prime_b, exp, private = build_numbers()
    modulus = prime_a * prime_b
    numbers = rsa.RSAPrivateNumbers(
        p=prime_a, q=prime_b, d=private,
        dmp1=private % (prime_a - 1), dmq1=private % (prime_b - 1),
        iqmp=pow(prime_b, -1, prime_a),
        public_numbers=rsa.RSAPublicNumbers(e=exp, n=modulus))
    key = numbers.private_key()
    name = x509.Name([
        x509.NameAttribute(NameOID.COMMON_NAME, "YORU Release"),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, "YORU"),
        x509.NameAttribute(NameOID.ORGANIZATIONAL_UNIT_NAME, "YORU Android"),
        x509.NameAttribute(NameOID.COUNTRY_NAME, "RU")])
    serial = int.from_bytes(hashlib.sha256((SEED + "|serial").encode()).digest()[:16], "big") >> 1
    cert = (x509.CertificateBuilder()
            .subject_name(name).issuer_name(name)
            .public_key(key.public_key())
            .serial_number(serial)
            .not_valid_before(datetime.datetime(2026, 1, 1))
            .not_valid_after(datetime.datetime(2061, 1, 1))
            .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
            .sign(private_key=key, algorithm=hashes.SHA256()))
    p12 = pkcs12.serialize_key_and_certificates(
        ALIAS.encode(),
        key, cert, None,
        serialization.BestAvailableEncryption(STORE_PASSWORD.encode()))
    with open(os.path.join(target, "yoru-release.p12"), "wb") as handle:
        handle.write(p12)
    with open(os.path.join(target, "signing.properties"), "w", encoding="utf-8") as handle:
        handle.write("storePassword=" + STORE_PASSWORD + "\n")
        handle.write("keyAlias=" + ALIAS + "\n")
        handle.write("keyPassword=" + STORE_PASSWORD + "\n")
    fingerprint = hashlib.sha256(cert.public_bytes(serialization.Encoding.DER)).hexdigest()
    print("SHA-256: " + fingerprint)
    return 0


if __name__ == "__main__":
    sys.exit(main())
