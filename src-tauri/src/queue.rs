use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use tauri::command;

/// Queue data for a single server
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ServerQueueData {
    #[serde(rename = "QueuePosition")]
    pub queue_position: i32,
    #[serde(rename = "Last Updated")]
    pub last_updated: Option<i64>,
    #[serde(rename = "Region")]
    pub region: Option<String>,
    #[serde(default)]
    pub eta: Option<i64>,
}

/// Response from the queue API
#[derive(Debug, Deserialize)]
pub struct QueueApiResponse {
    pub status: bool,
    #[serde(default)]
    pub errors: Vec<String>,
    pub data: HashMap<String, ServerQueueData>,
}

/// Server mapping info from the config API
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerMappingInfo {
    pub title: String,
    pub region: String,
    #[serde(default)]
    pub is4080_server: bool,
    #[serde(default)]
    pub is5080_server: bool,
    #[serde(default)]
    pub nuked: bool,
}

/// Response from the server mapping API
#[derive(Debug, Deserialize)]
pub struct ServerMappingApiResponse {
    pub status: bool,
    #[serde(default)]
    pub errors: Vec<String>,
    pub data: HashMap<String, ServerMappingInfo>,
}

/// Combined queue info for a single server (with mapping enrichment)
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EnrichedServerQueue {
    pub server_id: String,
    pub queue_position: i32,
    pub last_updated: Option<i64>,
    pub eta_seconds: Option<i64>,
    pub api_region: Option<String>,
    // From server mapping
    pub title: Option<String>,
    pub region: Option<String>,
    pub is_4080_server: bool,
    pub is_5080_server: bool,
    pub nuked: bool,
}

/// Full queue data response
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct QueueDataResponse {
    pub servers: Vec<EnrichedServerQueue>,
    pub last_fetched: i64,
}

const QUEUE_API_URL: &str = "https://api.printedwaste.com/gfn/queue/";
const SERVER_MAPPING_URL: &str = "https://remote.printedwaste.com/config/GFN_SERVERID_TO_REGION_MAPPING";

/// Fetch queue data from the external API
#[command]
pub async fn fetch_queue_data() -> Result<QueueDataResponse, String> {
    let client = reqwest::Client::new();

    // Fetch queue data
    let queue_response = client
        .get(QUEUE_API_URL)
        .header("Accept", "application/json")
        .send()
        .await
        .map_err(|e| format!("Failed to fetch queue data: {}", e))?;

    if !queue_response.status().is_success() {
        let status = queue_response.status();
        let body = queue_response.text().await.unwrap_or_default();
        return Err(format!("Queue API failed with status {}: {}", status, body));
    }

    let queue_data: QueueApiResponse = queue_response
        .json()
        .await
        .map_err(|e| format!("Failed to parse queue response: {}", e))?;

    if !queue_data.status {
        return Err(format!("Queue API returned error: {:?}", queue_data.errors));
    }

    // Fetch server mapping
    let mapping_response = client
        .get(SERVER_MAPPING_URL)
        .header("Accept", "application/json")
        .send()
        .await
        .map_err(|e| format!("Failed to fetch server mapping: {}", e))?;

    let server_mapping: HashMap<String, ServerMappingInfo> = if mapping_response.status().is_success() {
        match mapping_response.json::<ServerMappingApiResponse>().await {
            Ok(resp) if resp.status => resp.data,
            _ => HashMap::new(),
        }
    } else {
        HashMap::new()
    };

    // Combine queue data with server mapping
    let mut servers: Vec<EnrichedServerQueue> = queue_data
        .data
        .into_iter()
        .map(|(server_id, queue_info)| {
            let mapping = server_mapping.get(&server_id);
            EnrichedServerQueue {
                server_id: server_id.clone(),
                queue_position: queue_info.queue_position,
                last_updated: queue_info.last_updated,
                eta_seconds: queue_info.eta.map(|e| e / 1000), // Convert ms to seconds
                api_region: queue_info.region,
                title: mapping.map(|m| m.title.clone()),
                region: mapping.map(|m| m.region.clone()),
                is_4080_server: mapping.map(|m| m.is4080_server).unwrap_or(false),
                is_5080_server: mapping.map(|m| m.is5080_server).unwrap_or(false),
                nuked: mapping.map(|m| m.nuked).unwrap_or(false),
            }
        })
        .collect();

    // Sort by queue position (lowest first)
    servers.sort_by(|a, b| a.queue_position.cmp(&b.queue_position));

    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs() as i64;

    Ok(QueueDataResponse {
        servers,
        last_fetched: now,
    })
}

/// Fetch only server mapping (for caching or standalone use)
#[command]
pub async fn fetch_server_mapping() -> Result<HashMap<String, ServerMappingInfo>, String> {
    let client = reqwest::Client::new();

    let response = client
        .get(SERVER_MAPPING_URL)
        .header("Accept", "application/json")
        .send()
        .await
        .map_err(|e| format!("Failed to fetch server mapping: {}", e))?;

    if !response.status().is_success() {
        let status = response.status();
        let body = response.text().await.unwrap_or_default();
        return Err(format!("Server mapping API failed with status {}: {}", status, body));
    }

    let mapping_response: ServerMappingApiResponse = response
        .json()
        .await
        .map_err(|e| format!("Failed to parse server mapping response: {}", e))?;

    if !mapping_response.status {
        return Err(format!("Server mapping API returned error: {:?}", mapping_response.errors));
    }

    Ok(mapping_response.data)
}
