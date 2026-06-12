export * from './ticket.types';
export * from './inventory.types';
export * from './finance.types';
export * from './user.types';
export * from './appointment.types';
export * from './faq.types';

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}
