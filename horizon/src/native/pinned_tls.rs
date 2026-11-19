//! The source is compiled by the SDK's game patch and by the local TLS tests.
//! The patch reuses the exact game build's Rustls and its ring provider through ureq.

use std::{
    io::{self, Read, Write},
    net::TcpStream,
    sync::Arc,
};

#[cfg(test)]
use crate::PinnedTlsStream;
#[cfg(not(test))]
use minelights_horizon::PinnedTlsStream;
#[cfg(not(test))]
use ureq::rustls;

use rustls::{
    ClientConfig, ClientConnection, StreamOwned,
    client::danger::{HandshakeSignatureValid, ServerCertVerified, ServerCertVerifier},
    crypto::{self, WebPkiSupportedAlgorithms},
    pki_types::{CertificateDer, ServerName, UnixTime},
};
use sha2::{Digest, Sha256};

struct RustlsStream(StreamOwned<ClientConnection, TcpStream>);

impl Read for RustlsStream {
    fn read(&mut self, buffer: &mut [u8]) -> io::Result<usize> {
        self.0.read(buffer)
    }
}

impl Write for RustlsStream {
    fn write(&mut self, buffer: &[u8]) -> io::Result<usize> {
        self.0.write(buffer)
    }

    fn flush(&mut self) -> io::Result<()> {
        self.0.flush()
    }
}

impl PinnedTlsStream for RustlsStream {
    fn socket(&self) -> &TcpStream {
        &self.0.sock
    }
}

pub fn connect(
    host: &str,
    expected: [u8; 32],
    mut socket: TcpStream,
) -> Result<Box<dyn PinnedTlsStream>, String> {
    let provider = Arc::new(crypto::ring::default_provider());
    let verifier = Arc::new(PinnedCertificateVerifier {
        expected,
        algorithms: provider.signature_verification_algorithms,
    });
    let config = ClientConfig::builder_with_provider(provider)
        .with_protocol_versions(&[&rustls::version::TLS13, &rustls::version::TLS12])
        .map_err(|error| format!("configure TLS versions: {error}"))?
        .dangerous()
        .with_custom_certificate_verifier(verifier)
        .with_no_client_auth();
    let server_name = ServerName::try_from(host.to_owned())
        .map_err(|_| "invalid TLS server name".to_owned())?;
    let mut connection = ClientConnection::new(Arc::new(config), server_name)
        .map_err(|error| format!("create TLS connection: {error}"))?;
    connection
        .complete_io(&mut socket)
        .map_err(|error| format!("TLS handshake failed: {error}"))?;
    let peer = connection
        .peer_certificates()
        .and_then(|certificates| certificates.first())
        .ok_or_else(|| "TLS peer provided no certificate".to_owned())?;
    let actual = Sha256::digest(peer.as_ref());
    if actual.as_slice() != expected {
        return Err("TLS certificate fingerprint did not match the paired server".into());
    }
    Ok(Box::new(RustlsStream(StreamOwned::new(connection, socket))))
}

#[derive(Debug)]
struct PinnedCertificateVerifier {
    expected: [u8; 32],
    algorithms: WebPkiSupportedAlgorithms,
}

impl ServerCertVerifier for PinnedCertificateVerifier {
    fn verify_server_cert(
        &self,
        end_entity: &CertificateDer<'_>,
        _intermediates: &[CertificateDer<'_>],
        _server_name: &ServerName<'_>,
        _ocsp_response: &[u8],
        _now: UnixTime,
    ) -> Result<ServerCertVerified, rustls::Error> {
        let actual = Sha256::digest(end_entity.as_ref());
        if actual.as_slice() == self.expected {
            Ok(ServerCertVerified::assertion())
        } else {
            Err(rustls::Error::InvalidCertificate(
                rustls::CertificateError::BadEncoding,
            ))
        }
    }

    fn verify_tls12_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &rustls::DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, rustls::Error> {
        crypto::verify_tls12_signature(message, cert, dss, &self.algorithms)
    }

    fn verify_tls13_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &rustls::DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, rustls::Error> {
        crypto::verify_tls13_signature(message, cert, dss, &self.algorithms)
    }

    fn supported_verify_schemes(&self) -> Vec<rustls::SignatureScheme> {
        self.algorithms.supported_schemes()
    }
}
