import { routes } from '../../app.routes';
import { ActionCode, FunctionCode } from '../../core/services/permission.service';

describe('management route authorization', () => {
  it('guards each core portal route with the same backend view permission', () => {
    const management = routes.find(route => route.path === 'management');
    const children = management?.children || [];
    const expected: Record<string, FunctionCode> = {
      dashboard: FunctionCode.HOTEL,
      properties: FunctionCode.HOTEL,
      'room-types': FunctionCode.ROOM_TYPE,
      rooms: FunctionCode.ROOM,
    };

    for (const [path, functionCode] of Object.entries(expected)) {
      const route = children.find(child => child.path === path);
      expect(route?.canActivate?.length).toBeGreaterThan(0);
      expect(route?.data?.['functionCode']).toBe(functionCode);
      expect(route?.data?.['actionCode']).toBe(ActionCode.VIEW);
    }
  });
});
