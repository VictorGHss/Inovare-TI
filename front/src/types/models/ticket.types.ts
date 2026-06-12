export interface AttachmentResponse {
  id: string;
  originalFilename: string;
  fileUrl: string;
  fileType: string;
}

export interface TicketTag {
  id: string;
  name: string;
  color: string;
  active: boolean;
  defaultResolution?: string | null;
}

export interface TicketItemRequest {
  itemId: string;
  itemName: string;
  quantity: number;
}

export interface Ticket {
  id: string;
  title: string;
  description: string | null;
  anydeskCode: string | null;
  status: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
  requesterId: string;
  requesterName: string;
  technicianId?: string | null;
  technician_id?: string | null;
  technician?: { id?: string | null } | string | null;
  assignedToId: string | null;
  assignedToName: string | null;
  categoryId: string;
  categoryName: string;
  requestedItemId: string | null;
  requestedItemName: string | null;
  requestedQuantity: number | null;
  requestedItems?: TicketItemRequest[] | null;
  isFromDiscord: boolean;
  slaDeadline: string | null;
  createdAt: string;
  closedAt: string | null;
  attachments: AttachmentResponse[];
  tags?: TicketTag[] | null;
  relatedTicketIds?: string[] | null;
  additionalUserIds?: string[] | null;
  solutionText?: string | null;
  assetId?: string | null;
  assetName?: string | null;
  isAssetCritical?: boolean;
}

export interface TicketCategory {
  id: string;
  name: string;
}


/** Categoria de chamado retornada pelo backend (inclui SLA base). */
export interface TicketCategoryResponse {
  id: string;
  name: string;
  baseSlaHours: number;
}

/** Payload para criação de uma nova categoria de chamado. */
export interface CreateTicketCategoryDto {
  name: string;
  baseSlaHours: number;
}

export interface ResolveTicketItemRequest {
  itemId: string;
  quantity: number;
  recipientUserId?: string;
}

export interface ResolveTicketRequest {
  resolutionNotes?: string;
  assetIdToDeliver?: string;
  inventoryItemIdToDeliver?: string;
  quantityToDeliver?: number;
  newAssetToDeliver?: {
    userId: string;
    name: string;
    patrimonyCode: string;
    categoryId: string;
    specifications?: string;
  };
  recipientUserId?: string;
  itemsToDeliver?: ResolveTicketItemRequest[];
}

export interface TicketAttachment {
  id: string;
  originalFilename: string;
  storedFilename: string;
  fileType: string;
  ticketId: string;
  uploadedAt: string;
}

export interface RequestedItemDto {
  itemId: string;
  quantity: number;
}

export interface CreateTicketDto {
  title: string;
  description: string;
  anydeskCode?: string;
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
  categoryId: string;
  requestedItemId?: string;
  requestedQuantity?: number;
  requestedItems?: RequestedItemDto[];
}

export interface TicketComment {
  id: string;
  content: string;
  authorId: string;
  authorName: string;
  createdAt: string;
}

export interface Article {
  id: string;
  title: string;
  content: string;
  authorId: string;
  authorName: string;
  tags: string | null;
  status: 'DRAFT' | 'PUBLISHED';
  createdAt: string;
  updatedAt: string | null;
}

export type ArticleStatus = 'DRAFT' | 'PUBLISHED';

export interface CreateArticleDto {
  title: string;
  content: string;
  tags?: string;
  status?: ArticleStatus;
}

export interface ArticleSearchResult {
  id: string;
  title: string;
}
